package minivcs.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RepositoryPathsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private RepositoryPaths paths;
    private Path root;

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("proj").toPath().toRealPath();
        paths = new RepositoryPaths(root);
    }

    private void assertRejected(String input) {
        try {
            paths.toRelative(input);
            fail("Should have rejected: " + input);
        } catch (VcsException expected) {
            // ok
        }
    }

    @Test
    public void normalisesRelativePaths() {
        assertEquals("src/Main.java", paths.toRelative("src/Main.java"));
        assertEquals("b.txt", paths.toRelative("a/../b.txt"));
        assertEquals("a/b.txt", paths.toRelative("./a/./b.txt"));
        assertEquals("", paths.toRelative("."));
    }

    @Test
    public void acceptsAbsolutePathInsideRoot() {
        assertEquals("x/y.txt", paths.toRelative(root.resolve("x").resolve("y.txt").toString()));
    }

    @Test
    public void rejectsEscapesAndInternalFolder() {
        assertRejected("../outside.txt");
        assertRejected("a/../../outside.txt");
        assertRejected(tmp.getRoot().toPath().resolve("other.txt").toString());
        assertRejected(".minivcs");
        assertRejected(".minivcs/objects/ab/cd");
        assertRejected("");
    }

    @Test
    public void validatesStoredKeys() {
        RepositoryPaths.validateKey("ok/file.txt");
        String[] bad = {"/abs", "a/../b", "a//b", "a\\b", ".minivcs/x", "trailing/", ".."};
        for (String b : bad) {
            try {
                RepositoryPaths.validateKey(b);
                fail("Should reject stored key " + b);
            } catch (VcsException expected) {
                // ok
            }
        }
    }
}
