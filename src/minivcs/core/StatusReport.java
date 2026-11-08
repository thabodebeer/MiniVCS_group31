/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Tamia
 * 
 * stores the results of the status command
 *
 * staged - changes ready for the next commit
 * modifiedNotStaged - tracked files changed since they were last staged or committed
 * deletedNotStaged - tracked files that are missing from the folder
 * untracked - new files that MiniVCS does not track and that are not ignored
 */
public class StatusReport {

    private final Commit head;
    private final List<FileChange> staged;
    private final List<String> modifiedNotStaged;
    private final List<String> deletedNotStaged;
    private final List<String> untracked;
    private final List<String> warnings;

    public StatusReport(Commit head, List<FileChange> staged, List<String> modifiedNotStaged,
                        List<String> deletedNotStaged, List<String> untracked, List<String> warnings) {
        this.head = head;
        this.staged = sortedCopy(staged);
        this.modifiedNotStaged = sortedCopy(modifiedNotStaged);
        this.deletedNotStaged = sortedCopy(deletedNotStaged);
        this.untracked = sortedCopy(untracked);
        this.warnings = List.copyOf(warnings);
    }

    private static <T extends Comparable<? super T>> List<T> sortedCopy(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.sort(copy);
        return Collections.unmodifiableList(copy);
    }

    public Commit getHead() { return head; }
    public List<FileChange> getStaged() { return staged; }
    public List<String> getModifiedNotStaged() { return modifiedNotStaged; }
    public List<String> getDeletedNotStaged() { return deletedNotStaged; }
    public List<String> getUntracked() { return untracked; }
    public List<String> getWarnings() { return warnings; }

    /** checks if there are no staged, changed, deleted or untracked files */
    public boolean isClean() {
        return staged.isEmpty() && modifiedNotStaged.isEmpty() && deletedNotStaged.isEmpty() && untracked.isEmpty();
    }

     /** creates readable status output for the CLI */
    public String format() {
        StringBuilder sb = new StringBuilder();
        if (head == null) {
            sb.append("No commits yet.\n");
        } else {
            sb.append("HEAD is ").append(head.getShortId()).append(" - ").append(head.getMessage()).append('\n');
        }
        if (!staged.isEmpty()) {
            sb.append("\nChanges staged for commit:\n");
            for (FileChange c : staged) {
                sb.append("    ").append(c).append('\n');
            }
        }
        if (!modifiedNotStaged.isEmpty() || !deletedNotStaged.isEmpty()) {
            sb.append("\nChanges not staged for commit (use 'add <path>'):\n");
            for (String p : modifiedNotStaged) {
                sb.append("    modified: ").append(p).append('\n');
            }
            for (String p : deletedNotStaged) {
                sb.append("    deleted:  ").append(p).append('\n');
            }
        }
        if (!untracked.isEmpty()) {
            sb.append("\nUntracked files (use 'add <path>' to track):\n");
            for (String p : untracked) {
                sb.append("    ").append(p).append('\n');
            }
        }
        if (isClean()) {
            sb.append("\nNothing to commit, working folder clean.\n");
        }
        for (String w : warnings) {
            sb.append("\nWarning: ").append(w).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}

