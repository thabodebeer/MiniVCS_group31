/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Tamia
 */
public class HistoryManager {

    static final int FORMAT_VERSION = 1;
    private static final int MIN_PREFIX = 4;

    /** only allow these classes when loading history.ser */
    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=20;maxbytes=536870912;"
            + "minivcs.core.HistoryData;minivcs.core.Commit;"
            + "java.util.ArrayList;java.util.TreeMap;"
            + "java.time.Ser;java.time.LocalDateTime;java.time.LocalDate;java.time.LocalTime;"
            + "java.lang.String;"
            + "java.lang.Object;" // needed for the ArrayList internal array
            + "!*");

    private final List<Commit> commits = new ArrayList<>();
    private final Map<String, Commit> byId = new HashMap<>();
    private String head; // latest commit id, or null if there are no commits
    private final Path historyFile;

    public HistoryManager(Path historyFile) {
        this.historyFile = historyFile;
    }

    /** adds a commit and makes it the latest commit */
    public void addCommit(Commit c) {
        if (byId.containsKey(c.getId())) {
            throw new VcsException("Commit " + c.getShortId() + " already exists.");
        }
        String expectedParent = head;
        if (expectedParent == null ? c.getParentId() != null : !expectedParent.equals(c.getParentId())) {
            throw new VcsException("New commit must point to the current HEAD as its parent.");
        }
        commits.add(c);
        byId.put(c.getId(), c);
        head = c.getId();
    }

    /**
     * finds a commit using its full id, a unique prefix of at least 4 characters
     * (like "a1b2c3d4"), or the word HEAD
     */
    public Commit getCommit(String idOrPrefix) {
        if (idOrPrefix == null || idOrPrefix.isBlank()) {
            throw new VcsException("Please give a commit ID.");
        }
        String wanted = idOrPrefix.trim().toLowerCase();
        if (wanted.equals("head")) {
            Commit h = getHead();
            if (h == null) {
                throw new VcsException("There are no commits yet.");
            }
            return h;
        }
        Commit exact = byId.get(wanted);
        if (exact != null) {
            return exact;
        }
        if (wanted.length() < MIN_PREFIX) {
            throw new VcsException("Commit ID is too short - give at least " + MIN_PREFIX + " characters.");
        }
        Commit match = null;
        for (Commit c : commits) {
            if (c.getId().startsWith(wanted)) {
                if (match != null) {
                    throw new VcsException("'" + idOrPrefix + "' matches more than one commit - type more characters.");
                }
                match = c;
            }
        }
        if (match == null) {
            throw new VcsException("No commit found with ID '" + idOrPrefix + "'. Use 'log' to see commit IDs.");
        }
        return match;
    }

    /** returns the latest commit, or null if there are none */
    public Commit getHead() {
        return head == null ? null : byId.get(head);
    }

    public void setHead(String id) {
        if (id != null && !byId.containsKey(id)) {
            throw new VcsException("Unknown commit: " + id);
        }
        head = id;
    }

    /** returns commits from newest to oldest by following parent links */
    public List<Commit> getLog() {
        List<Commit> log = new ArrayList<>();
        Commit current = getHead();
        while (current != null) {
            log.add(current);
            current = current.getParentId() == null ? null : byId.get(current.getParentId());
        }
        return log;
    }

    public List<Commit> getAllCommits() {
        return Collections.unmodifiableList(commits);
    }

    public int size() {
        return commits.size();
    }

    /** saves the history file safely */
    public void save() throws IOException {
        Files.createDirectories(historyFile.getParent());
        Path temp = Files.createTempFile(historyFile.getParent(), "history-", ".tmp");
        try {
            HistoryData data = new HistoryData(FORMAT_VERSION, new ArrayList<>(commits), head);
            try (ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeObject(data);
            }
            try {
                Files.move(temp, historyFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, historyFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** loads the history file and checks that its data is valid */
    public void load() throws IOException {
        commits.clear();
        byId.clear();
        head = null;
        if (!Files.exists(historyFile)) {
            return;
        }
        Object obj;
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(historyFile)))) {
            in.setObjectInputFilter(FILTER);
            obj = in.readObject();
        } catch (InvalidClassException e) {
            throw new VcsException("History file contains unexpected data and was rejected: " + historyFile, e);
        } catch (ClassNotFoundException | IOException | ClassCastException | IllegalStateException e) {
            throw new VcsException("History file is damaged and cannot be read: " + historyFile, e);
        }
        if (!(obj instanceof HistoryData data)) {
            throw new VcsException("History file has the wrong format: " + historyFile);
        }
        if (data.formatVersion != FORMAT_VERSION) {
            throw new VcsException("History file format version " + data.formatVersion
                    + " is not supported (expected " + FORMAT_VERSION + ").");
        }
        if (data.commits == null) {
            throw new VcsException("History file is damaged (no commit list).");
        }
        Set<String> seen = new HashSet<>();
        for (Commit c : data.commits) {
            if (c == null) {
                throw new VcsException("History file is damaged (empty commit entry).");
            }
            c.validate();
            if (c.getParentId() != null && !seen.contains(c.getParentId())) {
                throw new VcsException("History file is damaged: commit " + c.getShortId() + " has a missing parent.");
            }
            if (!seen.add(c.getId())) {
                throw new VcsException("History file is damaged: duplicate commit " + c.getShortId());
            }
            commits.add(c);
            byId.put(c.getId(), c);
        }
        if (data.head != null && !byId.containsKey(data.head)) {
            throw new VcsException("History file is damaged: HEAD points to an unknown commit.");
        }
        if (data.head == null && !commits.isEmpty()) {
            throw new VcsException("History file is damaged: commits exist but HEAD is empty.");
        }
        head = data.head;
    }
}