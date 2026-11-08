/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import minivcs.util.HashUtil;
/**
 *
 * @author Theo
 * 
 * stores one saved version of the project
 * once a commit is created, its details cannot be changed
 *
 * a commit stores file paths and their hashes instead of the actual file contents
 * the file contents are stored in BlobStore so the same content does not need to be saved again
 *
 * the commit ID is created using the commit details and file hashes
 * the same details always create the same ID
 * if any details change, the ID also changes
 * each commit links to the previous commit to keep track of the project history
 * validate checks that the commit data has not been changed or damaged
 *
 * this class can be saved to a file so the commit history can be loaded later
 */
public final class Commit implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String id;
    private final String message;
    private final String author;
    private final LocalDateTime timestamp;
    private final String parentId;              // null for the first commit
    private final TreeMap<String, String> fileHashes;

    private Commit(String id, String message, String author, LocalDateTime timestamp,
                   String parentId, TreeMap<String, String> fileHashes) {
        this.id = id;
        this.message = message;
        this.author = author;
        this.timestamp = timestamp;
        this.parentId = parentId;
        this.fileHashes = fileHashes;
    }

     /**here it creates a commit and calculates its ID */
    public static Commit create(String message, String author, LocalDateTime timestamp,
                                String parentId, Map<String, String> fileHashes) {
        TreeMap<String, String> copy = new TreeMap<>(fileHashes); // defensive, sorted copy
        String id = computeId(message, author, timestamp, parentId, copy);
        Commit commit = new Commit(id, message, author, timestamp, parentId, copy);
        commit.validate();
        return commit;
    }

    //** creates the commit ID using the commit details and file hashes */
    static String computeId(String message, String author, LocalDateTime timestamp,
                            String parentId, Map<String, String> fileHashes) {
        StringBuilder sb = new StringBuilder();
        sb.append("parent ").append(parentId == null ? "none" : parentId).append('\n');
        sb.append("author ").append(author).append('\n');
        sb.append("time ").append(timestamp).append('\n');
        sb.append("message ").append(message).append('\n');
        for (Map.Entry<String, String> e : new TreeMap<>(fileHashes).entrySet()) {
            sb.append(e.getValue()).append(' ').append(e.getKey()).append('\n');
        }
        return HashUtil.sha256(sb.toString());
    }

    /**
     * checks that the commit details are present and correct
     * and it also checks that the commit ID still matches the saved data
     * furthermore this is also used when loading commits from a file
     */
    public void validate() {
        if (message == null || author == null || timestamp == null || fileHashes == null
                || !HashUtil.isValidHash(id)) {
            throw new VcsException("Commit data is incomplete or damaged.");
        }
        if (parentId != null && !HashUtil.isValidHash(parentId)) {
            throw new VcsException("Commit " + getShortId() + " has an invalid parent ID.");
        }
        for (Map.Entry<String, String> e : fileHashes.entrySet()) {
            RepositoryPaths.validateKey(e.getKey());
            if (!HashUtil.isValidHash(e.getValue())) {
                throw new VcsException("Commit " + getShortId() + " has an invalid hash for " + e.getKey());
            }
        }
        if (!computeId(message, author, timestamp, parentId, fileHashes).equals(id)) {
            throw new VcsException("Commit " + getShortId() + " failed its integrity check (data was changed).");
        }
    }

    public String getId() { return id; }
    public String getShortId() { return HashUtil.shortHash(id); }
    public String getMessage() { return message; }
    public String getAuthor() { return author; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getParentId() { return parentId; }

    /** returns the file paths and hashes without allowing them to be changed */
    public Map<String, String> getFileHashes() {
        return Collections.unmodifiableMap(fileHashes);
    }

    public String getFormattedTime() {
        return timestamp.format(DISPLAY);
    }

     /** it shows a short summary of the commit in the history */
    @Override
    public String toString() {
        return getShortId() + "  " + getFormattedTime() + "  " + author + "  " + message
                + "  (" + fileHashes.size() + " file" + (fileHashes.size() == 1 ? "" : "s") + ")";
    }

     /** here it shows the full details of the commit */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("commit ").append(id).append('\n');
        sb.append("parent ").append(parentId == null ? "(none - first commit)" : parentId).append('\n');
        sb.append("author ").append(author).append('\n');
        sb.append("date   ").append(getFormattedTime()).append('\n');
        sb.append('\n').append("    ").append(message).append("\n\n");
        sb.append("Files in this snapshot (").append(fileHashes.size()).append("):\n");
        for (Map.Entry<String, String> e : fileHashes.entrySet()) {
            sb.append("  ").append(HashUtil.shortHash(e.getValue())).append("  ").append(e.getKey()).append('\n');
        }
        return sb.toString();
    }
}
