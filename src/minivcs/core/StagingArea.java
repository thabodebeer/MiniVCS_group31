/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import minivcs.util.HashUtil;

/**
 *
 * @author Tamia
 * 
 * stores the changes that will be included in the next commit
 *
 * uses a map to store file paths and their content hashes
 * a TreeMap keeps the entries sorted by path
 * the special value {@link #DELETED} marks files for deletion
 *
 * saves the staged changes in .minivcs/metadata/index.txt as a text file
 * checks saved entries when loading to detect damaged data
 */
public class StagingArea {

    /** marks a file for deletion in the next commit */
    public static final String DELETED = "-";
    private static final String HEADER = "# MiniVCS index v1";

    private final Map<String, String> staged = new TreeMap<>();
    private final Path indexFile;

    public StagingArea(Path indexFile) {
        this.indexFile = indexFile;
    }
/** adds a file's new content to staging */
    public void stage(String path, String hash) {
        RepositoryPaths.validateKey(path);
        if (!HashUtil.isValidHash(hash)) {
            throw new VcsException("Invalid hash for " + path);
        }
        staged.put(path, hash);
    }

     /** marks a tracked file for deletion */
    public void stageDeletion(String path) {
        RepositoryPaths.validateKey(path);
        staged.put(path, DELETED);
    }

    /** removes a path from staging and returns true if it was staged */
    public boolean unstage(String path) {
        return staged.remove(path) != null;
    }

    /** returns a read-only view of all staged entries */
    public Map<String, String> getStaged() {
        return Collections.unmodifiableMap(staged);
    }

    public String get(String path) {
        return staged.get(path);
    }

    public boolean isEmpty() {
        return staged.isEmpty();
    }

    public int size() {
        return staged.size();
    }

    /** clears staging after a commit has been saved successfully */
    public void clear() {
        staged.clear();
    }

    /** loads the index file, or starts with an empty staging area if it is missing */
    public void load() throws IOException {
        staged.clear();
        if (!Files.exists(indexFile)) {
            return;
        }
        List<String> lines = Files.readAllLines(indexFile, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab < 0) {
                throw new VcsException("Staging index is damaged (line " + (i + 1) + "): " + indexFile);
            }
            String hash = line.substring(0, tab);
            String path = line.substring(tab + 1);
            try {
                if (hash.equals(DELETED)) {
                    stageDeletion(path);
                } else {
                    stage(path, hash);
                }
            } catch (VcsException e) {
                staged.clear();
                throw new VcsException("Staging index is damaged (line " + (i + 1) + "): " + e.getMessage(), e);
            }
        }
    }

     /** saves the index file using a temporary file before replacing the old one */
    public void save() throws IOException {
        Files.createDirectories(indexFile.getParent());
        Path temp = Files.createTempFile(indexFile.getParent(), "index-", ".tmp");
        try {
            try (BufferedWriter w = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                w.write(HEADER);
                w.newLine();
                for (Map.Entry<String, String> e : staged.entrySet()) {
                    w.write(e.getValue() + "\t" + e.getKey());
                    w.newLine();
                }
            }
            try {
                Files.move(temp, indexFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, indexFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}


