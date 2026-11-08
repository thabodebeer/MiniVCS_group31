/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import minivcs.diff.DiffEngine;
import minivcs.scan.FileScanner;
import minivcs.scan.IgnoreRules;
import minivcs.storage.BlobStore;
import minivcs.util.ActivityLogger;
import minivcs.util.HashUtil;

/**
 *
 * @author Tamia
 * 
 * manages the main operations of MiniVCS and connects the helper classes
 *
 * the CLI and GUI use this class to work with the repository
 *
 * each operation loads saved data, performs the task, saves changes and logs the result
 *
 * methods are synchronized to prevent two tasks from running at the same time
 *
 * rollback restores files from an older commit and saves the restored state as a new commit
 * existing files are backed up before they are changed or removed
 * untracked files are never changed
 */
public class Repository {

    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final RepositoryPaths paths;
    private final IgnoreRules ignoreRules = new IgnoreRules();
    private final FileScanner scanner;
    private final BlobStore store;
    private final StagingArea staging;
    private final HistoryManager history;
    private final ActivityLogger logger;
    private final DiffEngine diffEngine = new DiffEngine();

    /** opens an existing folder without creating or changing anything */
    public Repository(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            throw new VcsException("Folder does not exist: " + folder);
        }
        Path real;
        try {
            real = folder.toRealPath();
        } catch (IOException e) {
            throw new VcsException("Cannot open folder: " + folder, e);
        }
        this.paths = new RepositoryPaths(real);
        this.scanner = new FileScanner(real, ignoreRules);
        this.store = new BlobStore(paths.getObjectsDir());
        this.staging = new StagingArea(paths.getIndexFile());
        this.history = new HistoryManager(paths.getHistoryFile());
        this.logger = new ActivityLogger(paths.getActivityLogFile());
    }

     /** returns the current operating system username */
    public static String defaultAuthor() {
        String name = System.getProperty("user.name");
        return name == null || name.isBlank() ? "unknown" : name;
    }

    public Path getRoot() {
        return paths.getRoot();
    }

    public boolean isInitialized() {
        return Files.isDirectory(paths.getMetaDir(), LinkOption.NOFOLLOW_LINKS)
                && Files.isDirectory(paths.getObjectsDir())
                && Files.isDirectory(paths.getMetadataDir());
    }

     // ------------------------------------------------------------------ init

    /**
     * creates the .minivcs folder structure
     * if the repository already exists, nothing changes and false is returned
     * existing user files are never changed
     */
    public synchronized boolean init() {
        if (isInitialized()) {
            logger.log("init: repository already exists, nothing changed");
            return false;
        }
        Path meta = paths.getMetaDir();
        if (Files.exists(meta, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(meta, LinkOption.NOFOLLOW_LINKS)) {
            throw new VcsException("Cannot initialise: a FILE named " + RepositoryPaths.META_DIR_NAME + " already exists here.");
        }
        try {
            Files.createDirectories(paths.getObjectsDir());
            Files.createDirectories(paths.getMetadataDir());
            Files.createDirectories(paths.getLogsDir());
            Files.createDirectories(paths.getBackupsDir());
            if (!Files.exists(paths.getHistoryFile())) {
                history.save(); // empty history
            }
            if (!Files.exists(paths.getIndexFile())) {
                staging.save(); // empty staging area
            }
        } catch (IOException e) {
            throw new VcsException("Could not create the repository: " + describe(e), e);
        }
        logger.log("init: created repository in " + paths.getRoot());
        return true;
    }

    // ------------------------------------------------------------------ add / unstage

    /**
     * stages a file, folder or all files using "."
     * saves new and changed file contents in the object store
     * stages deleted tracked files for removal and skips unchanged files
     *
     * @return the changes staged by this operation
     */
    public synchronized List<FileChange> add(String userPath) {
        return perform("add " + userPath, () -> {
            prepare();
            String key = paths.toRelative(userPath);
            Path abs = paths.toAbsolute(key);
            Map<String, String> head = headSnapshot();
            List<FileChange> changes = new ArrayList<>();

            if (Files.isDirectory(abs, LinkOption.NOFOLLOW_LINKS)) {
                if (scanner.isIgnored(key, true)) {
                    throw new VcsException("'" + key + "' is ignored by " + RepositoryPaths.IGNORE_FILE_NAME);
                }
                Map<String, String> hashes = scanner.hashFiles(workingFiles(head, key));
                for (Map.Entry<String, String> e : hashes.entrySet()) {
                    stageFile(e.getKey(), e.getValue(), head, changes);
                }
                stageMissingUnder(key, head, changes);
            } else if (Files.isRegularFile(abs, LinkOption.NOFOLLOW_LINKS)) {
                boolean tracked = head.containsKey(key) || staging.get(key) != null;
                if (!tracked && scanner.isIgnored(key, false)) {
                    throw new VcsException("'" + key + "' is ignored by " + RepositoryPaths.IGNORE_FILE_NAME);
                }
                stageFile(key, HashUtil.sha256(abs), head, changes);
            } else if (Files.exists(abs, LinkOption.NOFOLLOW_LINKS)) {
                throw new VcsException("Only normal files and folders can be added: " + userPath);
            } else if (!stageMissingUnder(key, head, changes)) {
                throw new VcsException("Path not found: " + userPath);
            }
            staging.save();
            logger.log("add " + (key.isEmpty() ? "." : key) + " -> " + changes.size() + " change(s) staged");
            return changes;
        });
    }

    /** stages a file when its content differs from the latest commit */
    private void stageFile(String key, String hash, Map<String, String> head, List<FileChange> changes)
            throws IOException {
        String headHash = head.get(key);
        String alreadyStaged = staging.get(key);
        if (hash.equals(headHash)) {
            staging.unstage(key); // removes the staged change if the file matches the latest commit
            return;
        }
        if (hash.equals(alreadyStaged)) {
            return; // skips content that is already staged
        }
        // saves the file content in the object store and uses the hash of the saved content
        String storedHash = store.store(paths.toAbsolute(key));
        if (storedHash.equals(headHash)) {
            staging.unstage(key);
            return;
        }
        staging.stage(key, storedHash);
        changes.add(new FileChange(headHash == null ? ChangeType.ADDED : ChangeType.MODIFIED, key));
    }

    /**
     * stages deleted tracked files and removes staged files that were deleted before committing
     * @return true if a tracked or staged path exists under the given folder
     */
    private boolean stageMissingUnder(String dirKey, Map<String, String> head, List<FileChange> changes) {
        boolean known = false;
        for (String tracked : head.keySet()) {
            if (!RepositoryPaths.isUnder(tracked, dirKey)) {
                continue;
            }
            known = true;
            if (!Files.exists(paths.toAbsolute(tracked), LinkOption.NOFOLLOW_LINKS)
                    && !StagingArea.DELETED.equals(staging.get(tracked))) {
                staging.stageDeletion(tracked);
                changes.add(new FileChange(ChangeType.DELETED, tracked));
            }
        }
        for (String stagedPath : new ArrayList<>(staging.getStaged().keySet())) {
            if (!head.containsKey(stagedPath) && RepositoryPaths.isUnder(stagedPath, dirKey)) {
                known = true;
                if (!Files.exists(paths.toAbsolute(stagedPath), LinkOption.NOFOLLOW_LINKS)) {
                    staging.unstage(stagedPath);
                }
            }
        }
        return known;
    }

    /**
     * removes a file or folder from the staging area without changing working files
     *
     * @return the number of entries removed from staging
     */
    public synchronized int unstage(String userPath) {
        return perform("unstage " + userPath, () -> {
            prepare();
            String key = paths.toRelative(userPath);
            int count = 0;
            for (String stagedPath : new ArrayList<>(staging.getStaged().keySet())) {
                if (RepositoryPaths.isUnder(stagedPath, key)) {
                    staging.unstage(stagedPath);
                    count++;
                }
            }
            if (count == 0) {
                throw new VcsException("Nothing staged at: " + userPath);
            }
            staging.save();
            logger.log("unstage " + (key.isEmpty() ? "." : key) + " -> " + count + " entr" + (count == 1 ? "y" : "ies"));
            return count;
        });
    }

    // ------------------------------------------------------------------ commit

    /**
     * saves staged changes as a new commit
     * clears staging only after the commit history has been saved successfully
     */
    public synchronized Commit commit(String message, String author) {
        return perform("commit", () -> {
            prepare();
            String msg = message == null ? "" : message.strip();
            if (msg.isEmpty()) {
                throw new VcsException("A commit message is required.");
            }
            if (msg.length() > MAX_MESSAGE_LENGTH) {
                throw new VcsException("Commit message is too long (max " + MAX_MESSAGE_LENGTH + " characters).");
            }
            if (staging.isEmpty()) {
                throw new VcsException("Nothing to commit - stage changes first with 'add <path>'.");
            }
            Commit parent = history.getHead();
            Map<String, String> snapshot = new TreeMap<>(headSnapshot());
            for (Map.Entry<String, String> e : staging.getStaged().entrySet()) {
                if (StagingArea.DELETED.equals(e.getValue())) {
                    snapshot.remove(e.getKey()); // removes deleted files from the new snapshot
                } else {
                    if (!store.exists(e.getValue())) {
                        throw new VcsException("Staged content of " + e.getKey()
                                + " is missing from the object store - run 'add' on it again.");
                    }
                    snapshot.put(e.getKey(), e.getValue());
                }
            }
            if (parent != null && snapshot.equals(parent.getFileHashes())) {
                staging.clear();
                staging.save();
                throw new VcsException("Nothing to commit - the staged changes match the last commit.");
            }
            Commit commit = Commit.create(msg, cleanAuthor(author), LocalDateTime.now(),
                    parent == null ? null : parent.getId(), snapshot);
            history.addCommit(commit);
            history.save();     // saves the commit before clearing staging
            staging.clear();
            staging.save();
            logger.log("commit " + commit.getShortId() + " \"" + msg + "\" by " + commit.getAuthor()
                    + " (" + snapshot.size() + " files)");
            return commit;
        });
    }

    // ------------------------------------------------------------------ status / log / show

    /** compares the latest commit, staged changes and working files */
    public synchronized StatusReport status() {
        return perform("status", () -> {
            prepare();
            Map<String, String> head = headSnapshot();
            Map<String, String> staged = staging.getStaged();
            Map<String, String> working = scanner.hashFiles(workingFiles(head, ""));

            List<FileChange> stagedList = new ArrayList<>();
            for (Map.Entry<String, String> e : staged.entrySet()) {
                ChangeType type = StagingArea.DELETED.equals(e.getValue()) ? ChangeType.DELETED
                        : head.containsKey(e.getKey()) ? ChangeType.MODIFIED : ChangeType.ADDED;
                stagedList.add(new FileChange(type, e.getKey()));
            }

            List<String> modified = new ArrayList<>();
            List<String> untracked = new ArrayList<>();
            for (Map.Entry<String, String> e : working.entrySet()) {
                String path = e.getKey();
                String stagedHash = staged.get(path);
                String expected = stagedHash != null
                        ? (StagingArea.DELETED.equals(stagedHash) ? null : stagedHash)
                        : head.get(path);
                if (expected == null) {
                    untracked.add(path);
                } else if (!expected.equals(e.getValue())) {
                    modified.add(path);
                }
            }

            List<String> deleted = new ArrayList<>();
            for (String path : trackedPaths(head)) {
                if (!working.containsKey(path) && !StagingArea.DELETED.equals(staged.get(path))) {
                    deleted.add(path);
                }
            }
            logger.log("status");
            return new StatusReport(history.getHead(), stagedList, modified, deleted, untracked, scanner.getWarnings());
        });
    }

     /** returns all commits from newest to oldest */
    public synchronized List<Commit> log() {
        return perform("log", () -> {
            prepare();
            logger.log("log (" + history.size() + " commits)");
            return history.getLog();
        });
    }

    /** finds a commit using its ID or a unique ID prefix */
    public synchronized Commit getCommit(String spec) {
        return perform("show " + spec, () -> {
            prepare();
            return history.getCommit(spec);
        });
    }

    // ------------------------------------------------------------------ diff

    /** stores the details needed to compare two versions */
    private record Snapshot(String label, Map<String, String> files, boolean workingTree) { }

    /**
     * compares two versions of the project
     *
     * @param fromSpec commit ID or HEAD, with blank meaning HEAD
     * @param toSpec commit ID, HEAD or working, with blank meaning the working folder
     */
    public synchronized String diff(String fromSpec, String toSpec) {
        return perform("diff", () -> {
            prepare();
            Snapshot from = resolveSnapshot(fromSpec, true);
            Snapshot to = resolveSnapshot(toSpec, false);

            Set<String> allPaths = new TreeSet<>(from.files().keySet());
            allPaths.addAll(to.files().keySet());
            StringBuilder body = new StringBuilder();
            int changed = 0;
            for (String path : allPaths) {
                String oldHash = from.files().get(path);
                String newHash = to.files().get(path);
                if (oldHash != null && oldHash.equals(newHash)) {
                    continue;
                }
                changed++;
                long oldSize = oldHash == null ? 0 : sizeOf(from, path, oldHash);
                long newSize = newHash == null ? 0 : sizeOf(to, path, newHash);
                if (oldSize > DiffEngine.MAX_TEXT_BYTES || newSize > DiffEngine.MAX_TEXT_BYTES) {
                    String kind = oldHash == null ? "ADDED" : newHash == null ? "DELETED" : "MODIFIED";
                    body.append("=== ").append(kind).append(": ").append(path).append(" ===\n")
                        .append("File too large for a line diff (").append(oldSize).append(" -> ")
                        .append(newSize).append(" bytes).\n\n");
                    continue;
                }
                byte[] oldBytes = oldHash == null ? null : contentOf(from, path, oldHash);
                byte[] newBytes = newHash == null ? null : contentOf(to, path, newHash);
                body.append(diffEngine.describeFileDiff(path, oldBytes, newBytes)).append('\n');
            }
            logger.log("diff " + from.label() + " -> " + to.label() + " (" + changed + " files differ)");
            String header = "Comparing " + from.label() + "  ->  " + to.label() + "\n"
                    + (changed == 0 ? "No differences.\n" : changed + " file(s) differ.\n\n");
            return header + body;
        });
    }

    private Snapshot resolveSnapshot(String spec, boolean isFrom) throws IOException {
        boolean blank = spec == null || spec.isBlank();
        if ((blank && !isFrom) || (!blank && (spec.equalsIgnoreCase("working") || spec.equals(".")))) {
            Map<String, String> files = scanner.hashFiles(workingFiles(headSnapshot(), ""));
            return new Snapshot("working folder", files, true);
        }
        if (blank && history.getHead() == null) {
            return new Snapshot("empty repository", Collections.emptyMap(), false);
        }
        Commit c = history.getCommit(blank ? "HEAD" : spec);
        return new Snapshot(c.getShortId() + " (" + c.getMessage() + ")", c.getFileHashes(), false);
    }

    private long sizeOf(Snapshot s, String path, String hash) throws IOException {
        return s.workingTree() ? Files.size(paths.toAbsolute(path)) : store.size(hash);
    }

    private byte[] contentOf(Snapshot s, String path, String hash) throws IOException {
        return s.workingTree() ? Files.readAllBytes(paths.toAbsolute(path)) : store.get(hash);
    }

    // ------------------------------------------------------------------ rollback

    /** shows the changes a rollback would make without changing any files */
    public synchronized List<FileChange> previewRollback(String spec) {
        return perform("preview rollback " + spec, () -> {
            prepare();
            return planRollback(history.getCommit(spec));
        });
    }

    /** restores files from an old commit and saves the restored state as a new commit */
    public synchronized RollbackResult rollback(String spec, String author) {
        return perform("rollback " + spec, () -> {
            prepare();
            Commit target = history.getCommit(spec);
            // checks all saved content before changing any files
            for (String hash : new HashSet<>(target.getFileHashes().values())) {
                if (!store.exists(hash)) {
                    throw new VcsException("Cannot roll back: stored content " + HashUtil.shortHash(hash)
                            + " is missing. Nothing was changed.");
                }
            }
            List<FileChange> plan = planRollback(target);

            Path backupDir = null;
            for (FileChange c : plan) {
                Path file = paths.toAbsolute(c.path());
                if (c.type() != ChangeType.ADDED) {
                    if (backupDir == null) {
                        backupDir = paths.getBackupsDir().resolve(
                                LocalDateTime.now().format(BACKUP_STAMP) + "_before-rollback-to-" + target.getShortId());
                    }
                    Path copy = backupDir.resolve(c.path().replace('/', File.separatorChar));
                    Files.createDirectories(copy.getParent());
                    Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                }
                if (c.type() == ChangeType.DELETED) {
                    Files.delete(file);
                } else {
                    store.restore(target.getFileHashes().get(c.path()), file);
                }
            }

            staging.clear(); // removes staged changes that no longer apply after rollback
            Commit head = history.getHead();
            Commit newCommit = null;
            if (head == null || !head.getFileHashes().equals(target.getFileHashes())) {
                newCommit = Commit.create("Rollback to " + target.getShortId() + ": " + target.getMessage(),
                        cleanAuthor(author), LocalDateTime.now(), head == null ? null : head.getId(),
                        target.getFileHashes());
                history.addCommit(newCommit);
                history.save();
            }
            staging.save();
            logger.log("rollback to " + target.getShortId() + ": " + plan.size() + " file(s) changed"
                    + (newCommit == null ? "" : ", new commit " + newCommit.getShortId())
                    + (backupDir == null ? "" : ", backup in " + paths.getRoot().relativize(backupDir)));
            return new RollbackResult(target, newCommit, plan, backupDir);
        });
    }

    /**
     * works out which files must be restored, overwritten or removed during rollback
     * only tracked or staged files can be removed, so untracked files stay untouched
     */
    private List<FileChange> planRollback(Commit target) throws IOException {
        Map<String, String> wanted = target.getFileHashes();
        List<FileChange> plan = new ArrayList<>();
        for (Map.Entry<String, String> e : wanted.entrySet()) {
            Path file = paths.toAbsolute(e.getKey());
            checkParentsAreFolders(e.getKey());
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                throw new VcsException("Cannot restore " + e.getKey()
                        + ": a folder or link with that name exists. Rename it first. Nothing was changed.");
            }
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                plan.add(new FileChange(ChangeType.ADDED, e.getKey()));
            } else if (!HashUtil.sha256(file).equals(e.getValue())) {
                plan.add(new FileChange(ChangeType.MODIFIED, e.getKey()));
            }
        }
        for (String path : trackedPaths(headSnapshot())) {
            if (!wanted.containsKey(path) && Files.isRegularFile(paths.toAbsolute(path), LinkOption.NOFOLLOW_LINKS)) {
                plan.add(new FileChange(ChangeType.DELETED, path));
            }
        }
        Collections.sort(plan);
        return plan;
    }

    private void checkParentsAreFolders(String key) {
        int slash = key.indexOf('/');
        while (slash > 0) {
            Path parent = paths.toAbsolute(key.substring(0, slash));
            if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                throw new VcsException("Cannot restore " + key + ": '" + key.substring(0, slash)
                        + "' is a file, not a folder. Rename it first. Nothing was changed.");
            }
            slash = key.indexOf('/', slash + 1);
        }
    }

    // ------------------------------------------------------------------ info / log file

    /** returns the latest lines from the activity log */
    public synchronized List<String> readActivityLog(int max) {
        if (!isInitialized()) {
            throw new VcsException("This folder is not a MiniVCS repository yet. Run 'init' first.");
        }
        try {
            return logger.readRecent(max);
        } catch (IOException e) {
            throw new VcsException("Could not read the activity log: " + describe(e), e);
        }
    }

    /** returns a summary of the repository and its saved data */
    public synchronized Map<String, String> info() {
        return perform("info", () -> {
            prepare();
            Map<String, String> info = new LinkedHashMap<>();
            info.put("Folder", paths.getRoot().toString());
            info.put("Commits", String.valueOf(history.size()));
            Commit h = history.getHead();
            info.put("HEAD", h == null ? "(none)" : h.getShortId() + " - " + h.getMessage());
            info.put("Staged entries", String.valueOf(staging.size()));
            info.put("Stored objects", String.valueOf(store.countObjects()));
            info.put("Ignore rules", ignoreRules.getPatterns().isEmpty() ? "(none)" : String.join(", ", ignoreRules.getPatterns()));
            return info;
        });
    }

    // ------------------------------------------------------------------ helpers

    /** loads the latest saved data before each operation */
    private void prepare() throws IOException {
        if (!isInitialized()) {
            throw new VcsException("This folder is not a MiniVCS repository yet. Run 'init' first.");
        }
        ignoreRules.load(paths.getIgnoreFile());
        history.load();
        staging.load();
    }

    private Map<String, String> headSnapshot() {
        Commit head = history.getHead();
        return head == null ? Collections.emptyMap() : head.getFileHashes();
    }

    /** returns paths managed by MiniVCS through commits or staged additions */
    private Set<String> trackedPaths(Map<String, String> head) {
        Set<String> tracked = new TreeSet<>(head.keySet());
        for (Map.Entry<String, String> e : staging.getStaged().entrySet()) {
            if (!StagingArea.DELETED.equals(e.getValue())) {
                tracked.add(e.getKey());
            }
        }
        return tracked;
    }

    /**
     * finds files that should be checked under a folder
     * includes visible files and tracked files that still exist
     * ignore rules only hide untracked files
     */
    private List<String> workingFiles(Map<String, String> head, String dirKey) throws IOException {
        Set<String> files = new TreeSet<>(scanner.scan(paths.toAbsolute(dirKey)));
        for (String tracked : trackedPaths(head)) {
            if (RepositoryPaths.isUnder(tracked, dirKey)
                    && Files.isRegularFile(paths.toAbsolute(tracked), LinkOption.NOFOLLOW_LINKS)) {
                files.add(tracked);
            }
        }
        return new ArrayList<>(files);
    }

    private static String cleanAuthor(String author) {
        String a = author == null ? "" : author.replace('\n', ' ').replace('\r', ' ').strip();
        if (a.isEmpty()) {
            a = defaultAuthor();
        }
        return a.length() > 100 ? a.substring(0, 100) : a;
    }

     /** converts common file errors into simple messages */
    private static String describe(IOException e) {
        if (e instanceof NoSuchFileException) {
            return "File not found: " + e.getMessage();
        }
        if (e instanceof AccessDeniedException) {
            return "Permission denied: " + e.getMessage();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /** describes a task that may produce an input or output error */
    @FunctionalInterface
    private interface Action<T> {
        T run() throws IOException;
    }

    /**
     * runs an operation and logs errors before reporting them with a clear message
     * keeps the original error so it can be checked if needed
     */
    private <T> T perform(String name, Action<T> action) {
        try {
            return action.run();
        } catch (VcsException e) {
            logErrorSafely(name + ": " + e.getMessage());
            throw e;
        } catch (IOException e) {
            String message = describe(e);
            logErrorSafely(name + ": " + message);
            throw new VcsException(message, e);
        } catch (RuntimeException e) {
            logErrorSafely(name + ": unexpected error " + e);
            throw e;
        }
    }

     /** logs errors only when the repository already exists */
    private void logErrorSafely(String message) {
        if (isInitialized()) {
            logger.logError(message);
        }
    }
}

