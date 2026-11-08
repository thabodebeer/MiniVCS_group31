package minivcs.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HashUtilTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void knownValues() {
        // Official SHA-256 test vectors
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HashUtil.sha256("abc".getBytes(StandardCharsets.UTF_8)));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HashUtil.sha256(new byte[0]));
    }

    @Test
    public void fileHashEqualsByteHash() throws Exception {
        byte[] data = new byte[3 * 1024 * 1024 + 17]; // larger than the 8 KB buffer, odd size
        new Random(42).nextBytes(data);
        Path file = tmp.newFile("big.bin").toPath();
        Files.write(file, data);
        assertEquals(HashUtil.sha256(data), HashUtil.sha256(file));
    }

    @Test
    public void validHashCheck() {
        assertTrue(HashUtil.isValidHash(HashUtil.sha256("x")));
        assertFalse(HashUtil.isValidHash("../../etc/passwd"));
        assertFalse(HashUtil.isValidHash("ABC"));
        assertFalse(HashUtil.isValidHash(null));
    }
}
