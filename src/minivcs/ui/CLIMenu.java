/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.ui;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import minivcs.core.Commit;
import minivcs.core.FileChange;
import minivcs.core.Repository;
import minivcs.core.RollbackResult;
import minivcs.core.VcsException;

/**
 *
 * @author Tamia
 * 
 * supports an interactive menu and running one command from a terminal
 * the Repository class handles the main operations
 * this class reads commands and displays results and errors
 */
public class CLIMenu {

    private Repository repo;
    private final Scanner input;
    private final PrintStream out;
    private String author = Repository.defaultAuthor();
    private boolean lastCommandFailed;

    public CLIMenu(Repository repo, InputStream in, PrintStream out) {
        this.repo = repo;
        this.input = new Scanner(in);
        this.out = out;
    }

    /** starts the menu and reads command line arguments */
    public static void main(String[] args) {
        Path folder = null;
        List<String> commandWords = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--repo")) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: --repo needs a folder path");
                    System.exit(2);
                }
                folder = Paths.get(args[++i]);
            } else {
                commandWords.add(args[i]);
            }
        }
        CLIMenu menu = new CLIMenu(null, System.in, System.out);
        if (commandWords.isEmpty()) {
            menu.start(folder);
        } else {
            menu.openFolder(folder != null ? folder : Paths.get("").toAbsolutePath(), false);
            String command = commandWords.get(0);
            List<String> rest = commandWords.subList(1, commandWords.size());
            menu.execute(command, new ArrayList<>(rest), String.join(" ", rest));
            System.exit(menu.lastCommandFailed ? 1 : 0);
        }
    }

    /** runs the menu until the user exits or input ends */
    public void start(Path folder) {
        out.println("==============================================");
        out.println(" MiniVCS - simple version control (Group 31)");
        out.println("==============================================");
        if (folder == null) {
            String cwd = Paths.get("").toAbsolutePath().toString();
            out.println("Which project folder do you want to work in?");
            out.print("Folder path (press Enter for " + cwd + "): ");
            out.flush();
            String typed = input.hasNextLine() ? input.nextLine().strip() : "";
            try {
                folder = typed.isEmpty() ? Paths.get(cwd) : toPath(stripQuotes(typed));
            } catch (VcsException e) {
                error(e.getMessage() + " - use 'open <folder>' to try again.");
            }
        }
        if (folder != null) {
            openFolder(folder, true);
        }
        out.println("Type 'help' to see the commands.");
        while (true) {
            out.print("minivcs> ");
            out.flush();
            if (!input.hasNextLine()) {
                out.println();
                break;
            }
            if (!executeLine(input.nextLine())) {
                break;
            }
        }
        out.println("Goodbye.");
    }

    /** reads and runs one command entered by the user */
    public boolean executeLine(String line) {
        String trimmed = line == null ? "" : line.strip();
        if (trimmed.isEmpty()) {
            return true;
        }
        int space = indexOfWhitespace(trimmed);
        String command = space < 0 ? trimmed : trimmed.substring(0, space);
        String rawRest = space < 0 ? "" : trimmed.substring(space).strip();
        List<String> args;
        try {
            // keeps commit messages with apostrophes together
            args = command.equalsIgnoreCase("commit") ? new ArrayList<>() : CommandParser.tokenize(rawRest);
        } catch (VcsException e) {
            error(e.getMessage());
            return true;
        }
        return execute(command, args, rawRest);
    }

    /** runs a command and returns false when the user exits */
    public boolean execute(String command, List<String> args, String rawRest) {
        lastCommandFailed = false;
        String cmd = command.toLowerCase();
        try {
            switch (cmd) {
                case "help", "?" -> printHelp();
                case "exit", "quit" -> {
                    return false;
                }
                case "open" -> {
                    requireArgs(args, 1, "open <folder>");
                    openFolder(toPath(args.get(0)), true);
                }
                case "author" -> {
                    if (!args.isEmpty()) {
                        author = String.join(" ", args);
                    }
                    out.println("Author for new commits: " + author);
                }
                case "init" -> doInit();
                case "add" -> doAdd(args);
                case "unstage" -> {
                    requireArgs(args, 1, "unstage <path>");
                    int n = repo().unstage(args.get(0));
                    out.println("Unstaged " + n + " entr" + (n == 1 ? "y" : "ies") + ".");
                }
                case "status", "st" -> out.print(repo().status().format());
                case "commit" -> doCommit(rawRest);
                case "log", "history" -> doLog(args);
                case "show" -> {
                    requireArgs(args, 1, "show <commit-id>");
                    out.print(repo().getCommit(args.get(0)).toDetailedString());
                }
                case "diff" -> {
                    if (args.size() > 2) {
                        throw new VcsException("Usage: diff [from-commit] [to-commit]");
                    }
                    String from = args.size() >= 1 ? args.get(0) : null;
                    String to = args.size() >= 2 ? args.get(1) : null;
                    out.print(repo().diff(from, to));
                }
                case "rollback", "restore" -> doRollback(args);
                case "activity" -> {
                    int n = args.isEmpty() ? 20 : parsePositive(args.get(0));
                    for (String l : repo().readActivityLog(n)) {
                        out.println(l);
                    }
                }
                case "info" -> {
                    for (Map.Entry<String, String> e : repo().info().entrySet()) {
                        out.printf("%-15s %s%n", e.getKey() + ":", e.getValue());
                    }
                }
                default -> throw new VcsException("Unknown command '" + command + "'. Type 'help' for the list.");
            }
        } catch (VcsException e) {
            error(e.getMessage());
        } catch (RuntimeException e) {
            error("Unexpected problem: " + e);
        }
        return true;
    }

    private void doInit() {
        boolean created = repo().init();
        out.println(created
                ? "Initialised empty MiniVCS repository in " + repo.getRoot()
                : "Already a MiniVCS repository - nothing changed.");
    }

    private void doAdd(List<String> args) {
        requireArgs(args, 1, "add <file|folder|.>");
        int total = 0;
        for (String path : args) {
            List<FileChange> changes = repo().add(path);
            for (FileChange c : changes) {
                out.println("  staged " + c);
            }
            total += changes.size();
        }
        out.println(total == 0 ? "Nothing new to stage (files are unchanged)." : total + " change(s) staged.");
    }

    private void doCommit(String rawRest) {
        String message = rawRest;
        if (message.startsWith("-m ") || message.equals("-m")) {
            message = message.substring(2).strip();
        }
        message = stripQuotes(message);
        if (message.isEmpty()) {
            throw new VcsException("Usage: commit <message>   e.g.  commit \"Added the login page\"");
        }
        Commit c = repo().commit(message, author);
        out.println("Committed " + c.getShortId() + " - " + c.getMessage()
                + " (" + c.getFileHashes().size() + " files in snapshot)");
    }

    private void doLog(List<String> args) {
        int limit = args.isEmpty() ? Integer.MAX_VALUE : parsePositive(args.get(0));
        List<Commit> log = repo().log();
        if (log.isEmpty()) {
            out.println("No commits yet.");
            return;
        }
        out.println("ID        Date                 Author  Message");
        int shown = 0;
        for (Commit c : log) {
            if (shown++ >= limit) {
                break;
            }
            out.println((shown == 1 ? "* " : "  ") + c);
        }
        out.println("(* = HEAD, newest first)");
    }

    private void doRollback(List<String> args) {
        requireArgs(args, 1, "rollback <commit-id> [--yes]");
        String id = args.get(0);
        boolean skipConfirm = args.contains("--yes") || args.contains("-y");
        Commit target = repo().getCommit(id);
        List<FileChange> plan = repo().previewRollback(id);
        out.println("Rollback to " + target.getShortId() + " - " + target.getMessage());
        if (plan.isEmpty()) {
            out.println("The working folder already matches this commit; no files will change.");
        } else {
            out.println("These files will change (old versions are backed up first):");
            for (FileChange c : plan) {
                out.println("    " + c);
            }
        }
        if (!skipConfirm) {
            out.print("Continue? (yes/no): ");
            out.flush();
            String answer = input.hasNextLine() ? input.nextLine().strip().toLowerCase() : "";
            if (!answer.equals("yes") && !answer.equals("y")) {
                out.println("Rollback cancelled - nothing was changed.");
                return;
            }
        }
        RollbackResult result = repo().rollback(id, author);
        out.print(result.format());
    }

    /** opens a project folder */
    public void openFolder(Path folder, boolean interactive) {
        try {
            repo = new Repository(folder);
            if (interactive) {
                out.println("Working folder: " + repo.getRoot());
                out.println(repo.isInitialized()
                        ? "MiniVCS repository found."
                        : "This folder is not a repository yet - type 'init' to create one.");
            }
        } catch (VcsException e) {
            error(e.getMessage());
        }
    }

    /** displays all available commands */
    public void printHelp() {
        out.println("Commands:");
        out.println("  open <folder>            choose the project folder to work in");
        out.println("  init                     turn the folder into a MiniVCS repository");
        out.println("  add <path> [more...]     stage a file, a folder, or '.' for everything");
        out.println("  unstage <path>           remove a file/folder from the staging area");
        out.println("  status                   show staged, modified, deleted and untracked files");
        out.println("  commit <message>         save the staged changes as a new snapshot");
        out.println("  author [name]            show/set the author name used for commits");
        out.println("  log [n]                  list commits, newest first");
        out.println("  show <id>                details and file list of one commit");
        out.println("  diff                     last commit vs working folder");
        out.println("  diff <id>                that commit vs working folder");
        out.println("  diff <id1> <id2>         compare two commits");
        out.println("  rollback <id> [--yes]    restore the files of a commit (asks first, makes a backup)");
        out.println("  activity [n]             show the last n lines of the activity log");
        out.println("  info                     repository summary");
        out.println("  help                     this list");
        out.println("  exit                     quit");
        out.println("Commit IDs can be shortened to their first 4+ characters, or use HEAD.");
    }

    // helpers

    private Repository repo() {
        if (repo == null) {
            throw new VcsException("No folder is open. Use: open <folder>");
        }
        return repo;
    }

    private static void requireArgs(List<String> args, int count, String usage) {
        if (args.size() < count) {
            throw new VcsException("Usage: " + usage);
        }
    }

    private static int parsePositive(String text) {
        try {
            int n = Integer.parseInt(text);
            if (n <= 0) {
                throw new NumberFormatException();
            }
            return n;
        } catch (NumberFormatException e) {
            throw new VcsException("Expected a positive number but got '" + text + "'");
        }
    }

    private static Path toPath(String text) {
        try {
            return Paths.get(text);
        } catch (InvalidPathException e) {
            throw new VcsException("Invalid folder path: " + text);
        }
    }

    private static String stripQuotes(String s) {
        String t = s.strip();
        if (t.length() >= 2 && (t.startsWith("\"") && t.endsWith("\"") || t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length() - 1).strip();
        }
        return t;
    }

    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private void error(String message) {
        lastCommandFailed = true;
        out.println("Error: " + message);
    }

    public boolean lastCommandFailed() {
        return lastCommandFailed;
    }
}