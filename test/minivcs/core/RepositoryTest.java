package minivcs.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import minivcs.util.HashUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * End-to-end tests of the Repository facade, covering the normal flow and the
 * edge cases listed in the assignment test plan.
 */
public class RepositoryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path root;
    private Repository repo;

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("project").toPath();
        repo = new Repository(root);
        assertTrue(repo.init());
    }

    // ---------------------------------------------------------------- helpers

    private Path write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private String read(String path) throws IOException {
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }

    private Commit addAllAndCommit(String message) {
        repo.add(".");
        return repo.commit(message, "tester");
    }

    private void expectError(Runnable action, String expectedText) {
        try {
            action.run();
            fail("Expected an error containing: " + expectedText);
        } catch (VcsException e) {
            assertTrue("Message was: " + e.getMessage(),
                    e.getMessage().toLowerCase().contains(expectedText.toLowerCase()));
        }
    }

    private long objectCount() throws IOException {
        try (Stream<Path> s = Files.walk(root.resolve(".minivcs/objects"))) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    // ---------------------------------------------------------------- init

    @Test
    public void initIsSafeToRepeatAndCreatesLayout() throws Exception {
        write("keep.txt", "my work");
        assertFalse(repo.init()); // second time: nothing changes
        assertTrue(Files.isDirectory(root.resolve(".minivcs/objects")));
        assertTrue(Files.isDirectory(root.resolve(".minivcs/metadata")));
        assertTrue(Files.isDirectory(root.resolve(".minivcs/logs")));
        assertEquals("my work", read("keep.txt"));
    }

    @Test
    public void commandsBeforeInitGiveClearError() throws Exception {
        Path other = tmp.newFolder("not-a-repo").toPath();
        Repository r = new Repository(other);
        assertFalse(r.isInitialized());
        expectError(r::status, "run 'init' first");
        assertFalse("must not create .minivcs as a side effect", Files.exists(other.resolve(".minivcs")));
    }

    // ---------------------------------------------------------------- empty repo

    @Test
    public void emptyRepository() {
        StatusReport s = repo.status();
        assertTrue(s.isClean());
        assertNull(s.getHead());
        assertTrue(repo.log().isEmpty());
        expectError(() -> repo.commit("nothing", "me"), "Nothing to commit");
        assertTrue(repo.diff(null, null).contains("No differences"));
    }

    // ---------------------------------------------------------------- basic flow

    @Test
    public void addCommitLogAndPersistence() throws Exception {
        write("readme.txt", "hello");
        write("src/app/Main.java", "class Main {}");     // nested folders
        write("empty.txt", "");                           // empty file

        StatusReport before = repo.status();
        assertEquals(3, before.getUntracked().size());

        List<FileChange> staged = repo.add(".");
        assertEquals(3, staged.size());
        Commit c1 = repo.commit("First commit", "Tamia");
        assertEquals(3, c1.getFileHashes().size());
        assertTrue(c1.getFileHashes().containsKey("src/app/Main.java"));
        assertNull(c1.getParentId());
        assertTrue(repo.status().isClean());

        write("readme.txt", "hello world");
        Commit c2 = addAllAndCommit("Second");
        assertEquals(c1.getId(), c2.getParentId());

        // log is newest first
        List<Commit> log = repo.log();
        assertEquals(2, log.size());
        assertEquals(c2.getId(), log.get(0).getId());

        // A brand-new Repository object (like restarting the app) sees the same history
        Repository reopened = new Repository(root);
        assertEquals(2, reopened.log().size());
        assertEquals(c2.getId(), reopened.getCommit("HEAD").getId());
        assertEquals(c1.getId(), reopened.getCommit(c1.getShortId()).getId()); // short ID lookup
    }

    @Test
    public void commitIdIsDeterministic() {
        java.time.LocalDateTime t = java.time.LocalDateTime.of(2026, 10, 1, 12, 0);
        java.util.Map<String, String> files = java.util.Map.of("a.txt", HashUtil.sha256("a"));
        Commit x = Commit.create("msg", "me", t, null, files);
        Commit y = Commit.create("msg", "me", t, null, files);
        Commit z = Commit.create("msg2", "me", t, null, files);
        assertEquals(x.getId(), y.getId());
        assertFalse(x.getId().equals(z.getId()));
    }

    @Test
    public void unchangedFileIsNotStagedAgain() throws Exception {
        write("a.txt", "same");
        addAllAndCommit("one");
        assertTrue(repo.add("a.txt").isEmpty());
        assertTrue(repo.status().isClean());
        expectError(() -> repo.commit("again", "me"), "Nothing to commit");
    }

    @Test
    public void modifiedNewAndDeletedFilesAreDetected() throws Exception {
        write("keep.txt", "k");
        write("change.txt", "v1");
        write("remove.txt", "bye");
        addAllAndCommit("base");

        write("change.txt", "v2");
        write("new.txt", "new");
        Files.delete(root.resolve("remove.txt"));

        StatusReport s = repo.status();
        assertEquals(List.of("change.txt"), s.getModifiedNotStaged());
        assertEquals(List.of("remove.txt"), s.getDeletedNotStaged());
        assertEquals(List.of("new.txt"), s.getUntracked());

        repo.add(".");
        s = repo.status();
        assertEquals(3, s.getStaged().size());
        assertTrue(s.getStaged().contains(new FileChange(ChangeType.DELETED, "remove.txt")));
        assertTrue(s.getStaged().contains(new FileChange(ChangeType.MODIFIED, "change.txt")));
        assertTrue(s.getStaged().contains(new FileChange(ChangeType.ADDED, "new.txt")));

        Commit c = repo.commit("changes", "me");
        assertFalse("a deleted file is absent from the next snapshot", c.getFileHashes().containsKey("remove.txt"));
        assertTrue(c.getFileHashes().containsKey("keep.txt"));
    }

    @Test
    public void stagingASingleDeletedFile() throws Exception {
        write("gone.txt", "x");
        addAllAndCommit("base");
        Files.delete(root.resolve("gone.txt"));
        List<FileChange> changes = repo.add("gone.txt");
        assertEquals(new FileChange(ChangeType.DELETED, "gone.txt"), changes.get(0));
    }

    @Test
    public void fileEditedAfterStagingShowsAsModified() throws Exception {
        write("a.txt", "1");
        repo.add("a.txt");
        write("a.txt", "2");
        StatusReport s = repo.status();
        assertEquals(1, s.getStaged().size());
        assertEquals(List.of("a.txt"), s.getModifiedNotStaged());
    }

    @Test
    public void renameIsDeleteAndAdd() throws Exception {
        write("old-name.txt", "content");
        addAllAndCommit("base");
        Files.move(root.resolve("old-name.txt"), root.resolve("new-name.txt"));
        repo.add(".");
        StatusReport s = repo.status();
        assertTrue(s.getStaged().contains(new FileChange(ChangeType.DELETED, "old-name.txt")));
        assertTrue(s.getStaged().contains(new FileChange(ChangeType.ADDED, "new-name.txt")));
        long objectsBefore = objectCount();
        repo.commit("rename", "me");
        assertEquals("same content is not stored twice", objectsBefore, objectCount());
    }

    @Test
    public void unstage() throws Exception {
        write("a.txt", "1");
        write("dir/b.txt", "2");
        repo.add(".");
        assertEquals(1, repo.unstage("a.txt"));
        assertEquals(1, repo.unstage("dir"));
        assertTrue(repo.status().getStaged().isEmpty());
        expectError(() -> repo.unstage("a.txt"), "Nothing staged");
    }

    @Test
    public void commitMessageIsRequired() throws Exception {
        write("a.txt", "1");
        repo.add(".");
        expectError(() -> repo.commit("   ", "me"), "message is required");
    }

    // ---------------------------------------------------------------- ignore rules

    @Test
    public void ignoredFilesAreSkipped() throws Exception {
        write(".minivcsignore", "# test rules\n*.log\nbuild/\n");
        write("app.log", "noise");
        write("build/out.class", "binary-ish");
        write("src/A.java", "class A {}");
        repo.add(".");
        Commit c = repo.commit("with ignore", "me");
        assertTrue(c.getFileHashes().containsKey("src/A.java"));
        assertTrue(c.getFileHashes().containsKey(".minivcsignore"));
        assertFalse(c.getFileHashes().containsKey("app.log"));
        assertFalse(c.getFileHashes().containsKey("build/out.class"));
        expectError(() -> repo.add("app.log"), "ignored");
        expectError(() -> repo.add("build/out.class"), "ignored");
        assertTrue(repo.status().getUntracked().isEmpty());
    }

    // ---------------------------------------------------------------- deduplication

    @Test
    public void duplicateContentIsStoredOnce() throws Exception {
        write("a.txt", "same bytes");
        write("b.txt", "same bytes");
        write("sub/c.txt", "same bytes");
        repo.add(".");
        assertEquals(1, objectCount());
        Commit c = repo.commit("dupes", "me");
        assertEquals(c.getFileHashes().get("a.txt"), c.getFileHashes().get("sub/c.txt"));
    }

    // ---------------------------------------------------------------- path safety

    @Test
    public void invalidAndOutsidePathsAreRejected() throws Exception {
        Path outside = tmp.newFile("outside.txt").toPath();
        expectError(() -> repo.add("../outside.txt"), "outside");
        expectError(() -> repo.add(outside.toString()), "outside");
        expectError(() -> repo.add(".minivcs/metadata/history.ser"), "internal");
        expectError(() -> repo.add("does/not/exist.txt"), "not found");
    }

    // ---------------------------------------------------------------- diff

    @Test
    public void diffBetweenCommitsAndWorkingFolder() throws Exception {
        write("poem.txt", "roses are red\nviolets are blue\n");
        Commit c1 = addAllAndCommit("v1");
        write("poem.txt", "roses are red\nviolets are purple\nsugar is sweet\n");
        Commit c2 = addAllAndCommit("v2");

        String d = repo.diff(c1.getShortId(), c2.getShortId());
        assertTrue(d, d.contains("MODIFIED: poem.txt"));
        assertTrue(d, d.contains("- violets are blue"));
        assertTrue(d, d.contains("+ violets are purple"));
        assertTrue(d, d.contains("+ sugar is sweet"));

        write("poem.txt", "changed in working folder\n");
        String w = repo.diff(null, null); // HEAD vs working folder
        assertTrue(w, w.contains("+ changed in working folder"));
    }

    @Test
    public void binaryDiffIsSafe() throws Exception {
        Files.write(root.resolve("img.bin"), new byte[] {0, 1, 2, 3});
        Commit c1 = addAllAndCommit("bin1");
        Files.write(root.resolve("img.bin"), new byte[] {0, 9, 9});
        Commit c2 = addAllAndCommit("bin2");
        String d = repo.diff(c1.getId(), c2.getId());
        assertTrue(d, d.contains("Binary file"));
    }

    // ---------------------------------------------------------------- large file

    @Test
    public void largeFileRoundTrip() throws Exception {
        byte[] data = new byte[6 * 1024 * 1024]; // 6 MB, above the diff limit
        new Random(7).nextBytes(data);
        Files.write(root.resolve("big.dat"), data);
        Commit c1 = addAllAndCommit("big");
        Files.write(root.resolve("big.dat"), new byte[] {1});
        addAllAndCommit("small");
        assertTrue(repo.diff(c1.getId(), "HEAD").contains("too large"));
        repo.rollback(c1.getId(), "me");
        assertArrayEquals(data, Files.readAllBytes(root.resolve("big.dat")));
    }

    // ---------------------------------------------------------------- rollback

    @Test
    public void rollbackRestoresFilesBacksUpAndRecordsNewCommit() throws Exception {
        write("a.txt", "version 1");
        write("dir/b.txt", "b1");
        Commit c1 = addAllAndCommit("v1");

        write("a.txt", "version 2");
        write("c.txt", "added later");
        Files.delete(root.resolve("dir/b.txt"));
        addAllAndCommit("v2");

        write("a.txt", "local edit not committed");  // must be backed up, not lost
        write("untracked.txt", "do not touch");        // must survive

        List<FileChange> preview = repo.previewRollback(c1.getShortId());
        assertTrue(preview.contains(new FileChange(ChangeType.MODIFIED, "a.txt")));
        assertTrue(preview.contains(new FileChange(ChangeType.ADDED, "dir/b.txt")));
        assertTrue(preview.contains(new FileChange(ChangeType.DELETED, "c.txt")));
        assertEquals("preview changes nothing", "local edit not committed", read("a.txt"));

        RollbackResult r = repo.rollback(c1.getShortId(), "me");
        assertEquals("version 1", read("a.txt"));
        assertEquals("b1", read("dir/b.txt"));
        assertFalse(Files.exists(root.resolve("c.txt")));
        assertEquals("do not touch", read("untracked.txt"));

        assertNotNull(r.backupDir());
        assertEquals("local edit not committed", Files.readString(r.backupDir().resolve("a.txt")));
        assertEquals("added later", Files.readString(r.backupDir().resolve("c.txt")));

        assertNotNull(r.newCommit());
        assertEquals(3, repo.log().size()); // v1, v2, rollback - nothing deleted from history
        assertEquals(c1.getFileHashes(), repo.getCommit("HEAD").getFileHashes());
        assertEquals(List.of("untracked.txt"), repo.status().getUntracked());
    }

    @Test
    public void rollbackToHeadDiscardsLocalEditsWithoutNewCommit() throws Exception {
        write("a.txt", "committed");
        addAllAndCommit("v1");
        write("a.txt", "oops");
        RollbackResult r = repo.rollback("HEAD", "me");
        assertNull(r.newCommit());
        assertEquals("committed", read("a.txt"));
        assertEquals(1, repo.log().size());
    }

    @Test
    public void unknownCommitId() throws Exception {
        write("a.txt", "1");
        addAllAndCommit("v1");
        expectError(() -> repo.rollback("zzzzzz", "me"), "No commit found");
        expectError(() -> repo.getCommit("ab"), "too short");
    }

    // ---------------------------------------------------------------- corruption

    @Test
    public void corruptHistoryFileGivesClearError() throws Exception {
        write("a.txt", "1");
        addAllAndCommit("v1");
        Files.writeString(root.resolve(".minivcs/metadata/history.ser"), "this is not a history file");
        expectError(() -> new Repository(root).log(), "damaged");
    }

    @Test
    public void corruptStagingIndexGivesClearError() throws Exception {
        Files.writeString(root.resolve(".minivcs/metadata/index.txt"), "garbage-without-tab\n");
        expectError(repo::status, "Staging index is damaged");
        Files.writeString(root.resolve(".minivcs/metadata/index.txt"),
                HashUtil.sha256("x") + "\t../../escape.txt\n");
        expectError(repo::status, "damaged");
    }

    @Test
    public void corruptObjectIsDetectedAndFileIsNotOverwritten() throws Exception {
        write("a.txt", "good content");
        Commit c1 = addAllAndCommit("v1");
        write("a.txt", "newer");
        addAllAndCommit("v2");

        String hash = c1.getFileHashes().get("a.txt");
        Path obj = root.resolve(".minivcs/objects").resolve(hash.substring(0, 2)).resolve(hash.substring(2));
        Files.writeString(obj, "tampered!");

        expectError(() -> repo.rollback(c1.getId(), "me"), "corrupt");
        assertEquals("newer", read("a.txt"));
        expectError(() -> repo.diff(c1.getId(), "HEAD"), "corrupt");
    }

    @Test
    public void missingObjectStopsRollbackBeforeChangingAnything() throws Exception {
        write("a.txt", "v1");
        Commit c1 = addAllAndCommit("v1");
        write("a.txt", "v2");
        addAllAndCommit("v2");
        String hash = c1.getFileHashes().get("a.txt");
        Files.delete(root.resolve(".minivcs/objects").resolve(hash.substring(0, 2)).resolve(hash.substring(2)));
        expectError(() -> repo.rollback(c1.getId(), "me"), "missing");
        assertEquals("v2", read("a.txt"));
    }

    // ---------------------------------------------------------------- logging

    @Test
    public void actionsAndErrorsAreLogged() throws Exception {
        write("a.txt", "1");
        addAllAndCommit("logged commit");
        try {
            repo.rollback("ffffffff", "me");
        } catch (VcsException expected) {
            // ok
        }
        String log = String.join("\n", repo.readActivityLog(100));
        assertTrue(log, log.contains("INFO  | init"));
        assertTrue(log, log.contains("logged commit"));
        assertTrue(log, log.contains("ERROR | rollback ffffffff"));
    }
}
