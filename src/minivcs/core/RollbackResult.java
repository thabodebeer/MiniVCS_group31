/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author Tamia
 * 
 * stores the result of a rollback operation
 *
 * @param target the commit whose files were restored
 * @param newCommit the new commit that records the rollback, or null if no new commit was needed
 * @param changes the files that were restored, changed or removed
 * @param backupDir the folder containing backups of previous files, or null if no backups were needed
 */
public record RollbackResult(Commit target, Commit newCommit, List<FileChange> changes, Path backupDir) {

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rolled back working folder to ").append(target.getShortId())
          .append(" (").append(target.getMessage()).append(")\n");
        if (changes.isEmpty()) {
            sb.append("No files needed to change.\n");
        } else {
            for (FileChange c : changes) {
                String verb = switch (c.type()) {
                    case ADDED -> "restored ";
                    case MODIFIED -> "reverted ";
                    case DELETED -> "removed  ";
                };
                sb.append("    ").append(verb).append(c.path()).append('\n');
            }
        }
        if (backupDir != null) {
            sb.append("Previous versions were backed up to: ").append(backupDir).append('\n');
        }
        if (newCommit != null) {
            sb.append("Recorded as new commit ").append(newCommit.getShortId())
              .append(" - nothing in the history was deleted.\n");
        }
        return sb.toString();
    }
}
