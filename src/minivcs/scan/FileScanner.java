/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.scan;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import minivcs.core.ChangeType;
import minivcs.core.RepositoryPaths;
import minivcs.core.VcsException;
import minivcs.util.HashUtil;

/**
 *
 * @author Tamia
 */
public class FileScanner {

    /** prefix for temporary restore files */
    private static final String RESTORE_TEMP_PREFIX = ".minivcs-restore-";
    private static final int PARALLEL_THRESHOLD = 16;

    private final Path root;
    private final IgnoreRules rules;
    private final List<String> warnings = new ArrayList<>();

    public FileScanner(Path root, IgnoreRules rules) {
        this.root = root;
        this.rules = rules;
    }

    /**
     * lists all files under the given folder and its subfolders
     */
    public List<String> scan(Path start) throws IOException {
        warnings.clear();
        List<String> found = new ArrayList<>();
        if (Files.isDirectory(start, LinkOption.NOFOLLOW_LINKS)) {
            scanDirectory(start, found);
        }
        Collections.sort(found);
        return found;
    }

    /** searches through a folder and its subfolders */
    private void scanDirectory(Path dir, List<String> found) throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                if (Files.isSymbolicLink(entry)) {
                    continue; /// skip symbolic links
                }
                String key = RepositoryPaths.toKey(root.relativize(entry));
                boolean isDir = Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS);
                if (isIgnoredEntry(key, isDir)) {
                    continue;
                }
                if (isDir) {
                    scanDirectory(entry, found); // search inside the subfolder
                } else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    found.add(key);
                }
            }
        } catch (AccessDeniedException e) {
            // skip folders that cannot be opened and save a warning
            warnings.add("Skipped folder (access denied): " + RepositoryPaths.toKey(root.relativize(dir)));
        }
    }

    /** checks whether a file or folder should be skipped */
    private boolean isIgnoredEntry(String key, boolean isDirectory) {
        String name = key.substring(key.lastIndexOf('/') + 1);
        return RepositoryPaths.isInternal(key)
                || name.startsWith(RESTORE_TEMP_PREFIX)
                || rules.matches(key, isDirectory);
    }

    /**
     * checks the path and all its parent folders against the ignore rules
     */
    public boolean isIgnored(String key, boolean isDirectory) {
        if (key.isEmpty()) {
            return false;
        }
        String[] parts = key.split("/");
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                current.append('/');
            }
            current.append(parts[i]);
            boolean last = i == parts.length - 1;
            if (isIgnoredEntry(current.toString(), last ? isDirectory : true)) {
                return true;
            }
        }
        return false;
    }

    /** returns warnings from the last scan */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * calculates hashes for multiple files
     * uses multiple threads when there are 16 or more files
     *
     * @return sorted map of file paths and their SHA-256 hashes
     */
    public Map<String, String> hashFiles(Collection<String> keys) throws IOException {
        Map<String, String> result = new TreeMap<>();
        if (keys.size() < PARALLEL_THRESHOLD) {
            for (String key : keys) {
                result.put(key, HashUtil.sha256(resolve(key)));
            }
            return result;
        }
        int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Map<String, Future<String>> futures = new TreeMap<>();
            for (String key : keys) {
                Path file = resolve(key);
                futures.put(key, pool.submit(() -> HashUtil.sha256(file)));
            }
            for (Map.Entry<String, Future<String>> e : futures.entrySet()) {
                result.put(e.getKey(), e.getValue().get()); // waits for that task
            }
            return result;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new VcsException("Hashing failed: " + cause, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VcsException("Hashing was interrupted", e);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * compares the last saved snapshot with the current files
     */
    public static Map<String, ChangeType> detectChanges(Map<String, String> lastSnapshot,
                                                        Map<String, String> current) {
        Map<String, ChangeType> changes = new TreeMap<>();
        for (Map.Entry<String, String> e : current.entrySet()) {
            String oldHash = lastSnapshot.get(e.getKey());
            if (oldHash == null) {
                changes.put(e.getKey(), ChangeType.ADDED);
            } else if (!oldHash.equals(e.getValue())) {
                changes.put(e.getKey(), ChangeType.MODIFIED);
            }
        }
        for (String path : lastSnapshot.keySet()) {
            if (!current.containsKey(path)) {
                changes.put(path, ChangeType.DELETED);
            }
        }
        return changes;
    }

    private Path resolve(String key) {
        return root.resolve(key.replace('/', java.io.File.separatorChar));
    }
}
