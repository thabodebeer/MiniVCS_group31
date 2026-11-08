package minivcs.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import minivcs.core.Commit;
import minivcs.core.FileChange;
import minivcs.core.Repository;
import minivcs.core.RollbackResult;
import minivcs.core.StatusReport;
import minivcs.core.VcsException;

/**
 * simple swing window for minivcs
 *
 * this class handles the window and buttons. the repository class does all
 * the version control work. slow tasks run in the background so the window
 * does not freeze. buttons are disabled while a task is running.
 */
public class MainGUI {

    /** one row in the working folder changes list */
    private record ChangeItem(String group, String path, boolean staged, Color color) {
        @Override
        public String toString() {
            return String.format("%-12s %s", group, path);
        }
    }

    /** stores the status and commit history used to update the lists */
    private record RefreshData(StatusReport status, List<Commit> log) { }

    private Repository repo;
    private final JFrame frame;
    private final JTextField folderField = new JTextField(35);
    private final JTextField authorField = new JTextField(Repository.defaultAuthor(), 12);
    private final JTextField messageField = new JTextField();
    private final DefaultListModel<ChangeItem> changesModel = new DefaultListModel<>();
    private final JList<ChangeItem> changesList = new JList<>(changesModel);
    private final DefaultTableModel historyModel;
    private final JTable historyTable;
    private final List<Commit> shownCommits = new ArrayList<>();
    private final JTextArea output = new JTextArea();
    private final JLabel statusBar = new JLabel("Choose a project folder to begin.");
    private final List<JComponent> repoControls = new ArrayList<>();
    private JButton initButton;
    private boolean busy;

    /** creates the window; repo is the folder to open at start, or null */
    public MainGUI(Repository repo) {
        this.repo = repo;
        frame = new JFrame("MiniVCS - Group 31");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        historyModel = new DefaultTableModel(new String[] {"ID", "Date", "Author", "Message", "Files"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable = new JTable(historyModel);
        frame.setContentPane(buildContent());
        frame.setSize(1150, 760);
        frame.setMinimumSize(new Dimension(850, 560));
        frame.setLocationRelativeTo(null);
        if (repo != null) {
            folderField.setText(repo.getRoot().toString());
        }
        updateControls();
    }

    public void showWindow() {
        frame.setVisible(true);
        if (repo != null) {
            appendOutput(repo.isInitialized() ? "Opened repository " + repo.getRoot()
                    : "This folder is not a MiniVCS repository yet. Click 'Initialise repository'.");
            refresh();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // if the look and feel fails, use the default and print the reason
                System.err.println("Using default look and feel: " + e.getMessage());
            }
            Repository start = null;
            if (args.length > 0) {
                try {
                    start = new Repository(Paths.get(args[0]));
                } catch (VcsException | InvalidPathException e) {
                    System.err.println("Could not open " + args[0] + ": " + e.getMessage());
                }
            }
            new MainGUI(start).showWindow();
        });
    }

    // -------------------- layout

    private JPanel buildContent() {
        // title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(Theme.HEADER);
        titleBar.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JLabel title = new JLabel("MiniVCS - Version Control System");
        title.setFont(Theme.TITLE);
        title.setForeground(Color.WHITE);
        titleBar.add(title, BorderLayout.WEST);

        // folder row
        JPanel folderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        folderRow.setOpaque(false);
        folderRow.add(new JLabel("Project folder:"));
        folderRow.add(folderField);
        folderField.addActionListener(e -> openFolder(folderField.getText()));
        folderRow.add(button("Browse...", e -> browse()));
        folderRow.add(button("Open", e -> openFolder(folderField.getText())));
        initButton = button("Initialise repository", e -> doInit());
        folderRow.add(initButton);
        folderRow.add(new JLabel("   Author:"));
        folderRow.add(authorField);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(titleBar, BorderLayout.NORTH);
        top.add(folderRow, BorderLayout.CENTER);

        // left side: file changes and commit box
        changesList.setFont(Theme.MONO);
        changesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        changesList.setCellRenderer(new ChangeRenderer());
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(Theme.titled("Working folder changes"));
        left.setOpaque(false);
        left.add(new JScrollPane(changesList), BorderLayout.CENTER);

        JPanel leftButtons = new JPanel(new GridLayout(0, 2, 4, 4));
        leftButtons.setOpaque(false);
        leftButtons.add(repoButton("Stage selected", e -> stageSelected()));
        leftButtons.add(repoButton("Stage all", e -> stageAll()));
        leftButtons.add(repoButton("Unstage selected", e -> unstageSelected()));
        leftButtons.add(repoButton("Status", e -> showStatus()));

        JPanel commitPanel = new JPanel(new BorderLayout(4, 4));
        commitPanel.setOpaque(false);
        commitPanel.add(new JLabel("Commit message:"), BorderLayout.NORTH);
        commitPanel.add(messageField, BorderLayout.CENTER);
        messageField.addActionListener(e -> doCommit());
        repoControls.add(messageField);
        commitPanel.add(repoButton("Commit", e -> doCommit()), BorderLayout.EAST);

        JPanel leftSouth = new JPanel(new BorderLayout(4, 10));
        leftSouth.setOpaque(false);
        leftSouth.add(leftButtons, BorderLayout.NORTH);
        leftSouth.add(commitPanel, BorderLayout.SOUTH);
        left.add(leftSouth, BorderLayout.SOUTH);

        // right side: commit history and actions
        historyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        historyTable.setRowHeight(22);
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(260);
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(40);
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.setBorder(Theme.titled("Commit history (newest first)"));
        right.setOpaque(false);
        right.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        JPanel rightButtons = new JPanel(new GridLayout(0, 2, 4, 4));
        rightButtons.setOpaque(false);
        rightButtons.add(repoButton("Show commit details", e -> showDetails()));
        rightButtons.add(repoButton("Diff selected vs working folder", e -> diffWithWorking()));
        rightButtons.add(repoButton("Diff two selected commits", e -> diffTwoCommits()));
        rightButtons.add(repoButton("Roll back to selected...", e -> rollbackSelected()));
        right.add(rightButtons, BorderLayout.SOUTH);

        JSplitPane middle = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        middle.setResizeWeight(0.45);
        middle.setBorder(null);
        middle.setOpaque(false);

        // bottom: output area
        output.setEditable(false);
        output.setFont(Theme.MONO);
        output.setBackground(Theme.OUTPUT_BG);
        JPanel outPanel = new JPanel(new BorderLayout(4, 4));
        outPanel.setBorder(Theme.titled("Output"));
        outPanel.setOpaque(false);
        outPanel.add(new JScrollPane(output), BorderLayout.CENTER);
        JPanel outButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        outButtons.setOpaque(false);
        outButtons.add(repoButton("Activity log", e -> showActivityLog()));
        outButtons.add(button("Clear output", e -> output.setText("")));
        outPanel.add(outButtons, BorderLayout.NORTH);
        outPanel.setPreferredSize(new Dimension(800, 250));

        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, middle, outPanel);
        main.setResizeWeight(0.55);
        main.setBorder(null);
        main.setOpaque(false);

        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 0));

        JPanel body = new JPanel(new BorderLayout(6, 6));
        body.setBackground(Theme.BACKGROUND);
        body.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        body.add(main, BorderLayout.CENTER);
        body.add(statusBar, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Theme.BACKGROUND);
        content.add(top, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        return content;
    }

    private static JButton button(String text, ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        Theme.style(b);
        return b;
    }

    /** creates a button that only works when a repository is open */
    private JButton repoButton(String text, ActionListener action) {
        JButton b = button(text, action);
        repoControls.add(b);
        return b;
    }

    /** shows each file change in a different colour */
    private static class ChangeRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ChangeItem item && !isSelected) {
                setForeground(item.color());
            }
            return this;
        }
    }

    // -------------------- actions

    private void browse() {
        String current = folderField.getText().strip();
        JFileChooser chooser = new JFileChooser(current.isEmpty() ? null : new File(current));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose the project folder");
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            folderField.setText(chooser.getSelectedFile().getAbsolutePath());
            openFolder(folderField.getText());
        }
    }

    private void openFolder(String text) {
        if (text == null || text.isBlank()) {
            showError(new VcsException("Type or browse to a project folder first."));
            return;
        }
        try {
            repo = new Repository(Paths.get(text.strip()));
            folderField.setText(repo.getRoot().toString());
            output.setText("");
            appendOutput(repo.isInitialized() ? "Opened repository " + repo.getRoot()
                    : "This folder is not a MiniVCS repository yet. Click 'Initialise repository'.");
            clearViews();
            updateControls();
            refresh();
        } catch (VcsException | InvalidPathException e) {
            showError(e);
        }
    }

    private void doInit() {
        runInBackground("Initialising...", () -> repo.init(), created -> {
            appendOutput(created ? "Initialised a new MiniVCS repository in " + repo.getRoot()
                    : "Already a MiniVCS repository - nothing changed.");
            refresh();
        });
    }

    private void stageSelected() {
        List<ChangeItem> selected = changesList.getSelectedValuesList();
        if (selected.isEmpty()) {
            info("Select one or more files in the 'Working folder changes' list first.");
            return;
        }
        runInBackground("Staging...", () -> {
            List<FileChange> all = new ArrayList<>();
            for (ChangeItem item : selected) {
                all.addAll(repo.add(item.path()));
            }
            return all;
        }, this::reportStaged);
    }

    private void stageAll() {
        runInBackground("Staging everything...", () -> repo.add("."), this::reportStaged);
    }

    private void reportStaged(List<FileChange> changes) {
        if (changes.isEmpty()) {
            appendOutput("Nothing new to stage (files are unchanged or already staged).");
        } else {
            StringBuilder sb = new StringBuilder("Staged " + changes.size() + " change(s):\n");
            for (FileChange c : changes) {
                sb.append("    ").append(c).append('\n');
            }
            appendOutput(sb.toString());
        }
        refresh();
    }

    private void unstageSelected() {
        List<ChangeItem> selected = changesList.getSelectedValuesList().stream().filter(ChangeItem::staged).toList();
        if (selected.isEmpty()) {
            info("Select one or more 'staged' entries in the list first.");
            return;
        }
        runInBackground("Unstaging...", () -> {
            int count = 0;
            for (ChangeItem item : selected) {
                count += repo.unstage(item.path());
            }
            return count;
        }, count -> {
            appendOutput("Unstaged " + count + " entr" + (count == 1 ? "y" : "ies") + ".");
            refresh();
        });
    }

    private void showStatus() {
        runInBackground("Checking status...", () -> new RefreshData(repo.status(), repo.log()), data -> {
            applyRefresh(data);
            setOutput("STATUS\n" + data.status().format());
        });
    }

    private void doCommit() {
        String message = messageField.getText().strip();
        if (message.isEmpty()) {
            info("Type a commit message first (what did you change?).");
            messageField.requestFocusInWindow();
            return;
        }
        String author = authorField.getText();
        runInBackground("Committing...", () -> repo.commit(message, author), commit -> {
            messageField.setText("");
            appendOutput("Committed " + commit.getShortId() + " - " + commit.getMessage()
                    + " (" + commit.getFileHashes().size() + " files in snapshot)");
            refresh();
        });
    }

    private void showDetails() {
        Commit c = singleSelectedCommit();
        if (c != null) {
            setOutput(c.toDetailedString());
        }
    }

    private void diffWithWorking() {
        Commit c = singleSelectedCommit();
        if (c != null) {
            runInBackground("Comparing...", () -> repo.diff(c.getId(), null), this::setOutput);
        }
    }

    private void diffTwoCommits() {
        int[] rows = historyTable.getSelectedRows();
        if (rows.length != 2) {
            info("Select exactly TWO commits (hold Ctrl and click) to compare them.");
            return;
        }
        Arrays.sort(rows);
        Commit newer = shownCommits.get(rows[0]); // the newest commit is at the top of the table
        Commit older = shownCommits.get(rows[1]);
        runInBackground("Comparing...", () -> repo.diff(older.getId(), newer.getId()), this::setOutput);
    }

    private void rollbackSelected() {
        Commit target = singleSelectedCommit();
        if (target == null) {
            return;
        }
        runInBackground("Preparing rollback...", () -> repo.previewRollback(target.getId()), plan -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Roll back the working folder to ").append(target.getShortId())
              .append(" - ").append(target.getMessage()).append("?\n\n");
            if (plan.isEmpty()) {
                sb.append("The folder already matches this commit; no files will change.\n");
            } else {
                sb.append("These files will change:\n");
                for (FileChange c : plan) {
                    sb.append("    ").append(c).append('\n');
                }
                sb.append("\nCurrent versions are copied to .minivcs/backups first.\n");
            }
            sb.append("The rollback is saved as a NEW commit, so no history is lost.\n");
            sb.append("Untracked files are not touched. Staged changes are cleared.");
            JTextArea text = new JTextArea(sb.toString(), Math.min(18, plan.size() + 8), 60);
            text.setEditable(false);
            int answer = JOptionPane.showConfirmDialog(frame, new JScrollPane(text), "Confirm rollback",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                String author = authorField.getText();
                runInBackground("Rolling back...", () -> repo.rollback(target.getId(), author), (RollbackResult r) -> {
                    setOutput(r.format());
                    refresh();
                });
            } else {
                appendOutput("Rollback cancelled - nothing was changed.");
            }
        });
    }

    private void showActivityLog() {
        runInBackground("Reading log...", () -> repo.readActivityLog(200),
                lines -> setOutput("ACTIVITY LOG (latest 200 lines)\n" + String.join("\n", lines)));
    }

    // -------------------- refresh lists

    /** reloads the status and history, then updates the list and table */
    private void refresh() {
        if (repo == null || !repo.isInitialized()) {
            clearViews();
            updateControls();
            return;
        }
        runInBackground("Refreshing...", () -> new RefreshData(repo.status(), repo.log()), this::applyRefresh);
    }

    private void applyRefresh(RefreshData data) {
        changesModel.clear();
        StatusReport s = data.status();
        for (FileChange c : s.getStaged()) {
            changesModel.addElement(new ChangeItem("staged (" + c.type().getSymbol() + ")", c.path(), true, Theme.STAGED));
        }
        for (String p : s.getModifiedNotStaged()) {
            changesModel.addElement(new ChangeItem("modified", p, false, Theme.MODIFIED));
        }
        for (String p : s.getDeletedNotStaged()) {
            changesModel.addElement(new ChangeItem("deleted", p, false, Theme.DELETED));
        }
        for (String p : s.getUntracked()) {
            changesModel.addElement(new ChangeItem("untracked", p, false, Theme.UNTRACKED));
        }
        historyModel.setRowCount(0);
        shownCommits.clear();
        for (Commit c : data.log()) {
            shownCommits.add(c);
            historyModel.addRow(new Object[] {c.getShortId(), c.getFormattedTime(), c.getAuthor(),
                c.getMessage(), c.getFileHashes().size()});
        }
        statusBar.setText(s.isClean() ? "Working folder clean - " + data.log().size() + " commit(s)."
                : changesModel.size() + " change(s) listed - " + data.log().size() + " commit(s).");
    }

    private void clearViews() {
        changesModel.clear();
        historyModel.setRowCount(0);
        shownCommits.clear();
    }

    // -------------------- helpers

    private Commit singleSelectedCommit() {
        int[] rows = historyTable.getSelectedRows();
        if (rows.length != 1) {
            info("Select exactly one commit in the history table first.");
            return null;
        }
        return shownCommits.get(rows[0]);
    }

    /**
     * runs the task in the background. when it finishes, the result is handled
     * on the edt. errors are shown in a message box and in the output area.
     */
    private <T> void runInBackground(String busyText, Callable<T> work, Consumer<T> onSuccess) {
        if (busy) {
            return;
        }
        setBusy(true, busyText);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                setBusy(false, "Ready");
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    showError(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void setBusy(boolean value, String text) {
        busy = value;
        statusBar.setText(text);
        frame.setCursor(value ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        updateControls();
    }

    private void updateControls() {
        boolean open = repo != null;
        boolean initialised = open && repo.isInitialized();
        initButton.setEnabled(open && !initialised && !busy);
        for (JComponent c : repoControls) {
            c.setEnabled(initialised && !busy);
        }
    }

    private void setOutput(String text) {
        output.setText(text);
        output.setCaretPosition(0);
    }

    private void appendOutput(String text) {
        if (output.getDocument().getLength() > 0) {
            output.append("\n");
        }
        output.append(text);
        output.setCaretPosition(output.getDocument().getLength());
    }

    private void info(String message) {
        JOptionPane.showMessageDialog(frame, message, "MiniVCS", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(Throwable t) {
        String message = t instanceof VcsException || t instanceof InvalidPathException
                ? t.getMessage() : "Unexpected problem: " + t;
        appendOutput("Error: " + message);
        statusBar.setText("Error - see output");
        JOptionPane.showMessageDialog(frame, message, "MiniVCS error", JOptionPane.ERROR_MESSAGE);
    }
}