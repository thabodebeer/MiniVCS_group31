/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author Tamia
 * 
 * manages repository paths and checks that they are safe
 *
 * creates paths for the object store, commit history, staging area, logs and backups
 *
 * stores file paths relative to the repository folder using '/' on all operating systems
 *
 * rejects paths that go outside the repository or access the internal .minivcs folder
 */
public final class RepositoryPaths {

    public static final String META_DIR_NAME = ".minivcs";
    public static final String IGNORE_FILE_NAME = ".minivcsignore";

    private final Path root;
    private final Path metaDir;
    private final Path objectsDir;
    private final Path metadataDir;
    private final Path logsDir;
    private final Path backupsDir;

    /** @param root the real (already resolved) path of the working folder */
    public RepositoryPaths(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.metaDir = this.root.resolve(META_DIR_NAME);
        this.objectsDir = metaDir.resolve("objects");
        this.metadataDir = metaDir.resolve("metadata");
        this.logsDir = metaDir.resolve("logs");
        this.backupsDir = metaDir.resolve("backups");
    }

    public Path getRoot() { return root; }
    public Path getMetaDir() { return metaDir; }
    public Path getObjectsDir() { return objectsDir; }
    public Path getMetadataDir() { return metadataDir; }
    public Path getLogsDir() { return logsDir; }
    public Path getBackupsDir() { return backupsDir; }
    public Path getHistoryFile() { return metadataDir.resolve("history.ser"); }
    public Path getIndexFile() { return metadataDir.resolve("index.txt"); }
    public Path getActivityLogFile() { return logsDir.resolve("activity.log"); }
    public Path getIgnoreFile() { return root.resolve(IGNORE_FILE_NAME); }

    /**
     * converts a user path into a safe relative path such as "src/Main.java"
     * returns an empty string when the path points to the repository root
     *
     * @throws VcsException if the path is invalid, outside the repository, a symbolic
     *                      link, or inside the internal .minivcs folder
     */
    public String toRelative(String userPath) {
        if (userPath == null || userPath.isBlank()) {
            throw new VcsException("Please give a file or folder path.");
        }
        Path given;
        try {
            given = Paths.get(userPath.trim());
        } catch (InvalidPathException e) {
            throw new VcsException("Invalid path: " + userPath, e);
        }
        Path absolute = (given.isAbsolute() ? given : root.resolve(given)).normalize();

        if (Files.isSymbolicLink(absolute)) {
            throw new VcsException("Symbolic links are not tracked: " + userPath);
        }
        // resolves existing paths to prevent linked parent folders from pointing outside
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            try {
                absolute = absolute.toRealPath();
            } catch (IOException e) {
                throw new VcsException("Cannot access path: " + userPath, e);
            }
        }
        if (!absolute.startsWith(root)) {
            throw new VcsException("Path is outside the repository folder: " + userPath);
        }
        String key = toKey(root.relativize(absolute));
        if (isInternal(key)) {
            throw new VcsException("The internal " + META_DIR_NAME + " folder cannot be staged or changed.");
        }
        return key;
    }

    /** converts a saved relative path back into a safe full path */
    public Path toAbsolute(String key) {
        if (key.isEmpty()) {
            return root;
        }
        validateKey(key);
        Path absolute = root.resolve(key.replace('/', java.io.File.separatorChar)).normalize();
        if (!absolute.startsWith(root)) {
            throw new VcsException("Unsafe path in repository data: " + key);
        }
        return absolute;
    }

    /** joins path parts using '/' on Windows*/
    public static String toKey(Path relative) {
        StringBuilder sb = new StringBuilder();
        for (Path part : relative) {
            String name = part.toString();
            if (name.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(name);
        }
        return sb.toString();
    }

     /** checks if a path is .minivcs or is inside it */
    public static boolean isInternal(String key) {
        return key.equals(META_DIR_NAME) || key.startsWith(META_DIR_NAME + "/");
    }

    /** checks if a path is a folder or is inside that folder */
    public static boolean isUnder(String key, String dirKey) {
        return dirKey.isEmpty() || key.equals(dirKey) || key.startsWith(dirKey + "/");
    }

    /**
     * Strict check used when reading saved metadata (staging index, commits), so a
     * damaged or tampered file can never make MiniVCS write outside the repository.
     */
    public static void validateKey(String key) {
        if (key == null || key.isEmpty() || key.startsWith("/") || key.endsWith("/")
                || key.indexOf('\\') >= 0 || key.indexOf('\0') >= 0) {
            throw new VcsException("Invalid stored path: " + key);
        }
        for (String part : key.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new VcsException("Invalid stored path: " + key);
            }
        }
        if (isInternal(key)) {
            throw new VcsException("Stored path points into the internal folder: " + key);
        }
    }
}

