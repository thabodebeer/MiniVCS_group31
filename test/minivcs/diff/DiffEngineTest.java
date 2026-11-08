package minivcs.diff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import minivcs.diff.DiffEngine.DiffLine;
import minivcs.diff.DiffEngine.Op;
import org.junit.Test;

public class DiffEngineTest {

    private final DiffEngine engine = new DiffEngine();

    private static long count(List<DiffLine> lines, Op op) {
        return lines.stream().filter(l -> l.op() == op).count();
    }

    @Test
    public void identicalFilesHaveNoChanges() {
        List<String> a = Arrays.asList("one", "two", "three");
        List<DiffLine> d = engine.diff(a, a);
        assertEquals(3, count(d, Op.EQUAL));
        assertEquals(0, count(d, Op.ADDED) + count(d, Op.REMOVED));
    }

    @Test
    public void insertDeleteReplace() {
        List<String> a = Arrays.asList("a", "b", "c", "d");
        List<String> b = Arrays.asList("a", "x", "c", "d", "e");
        List<DiffLine> d = engine.diff(a, b);
        assertEquals(3, count(d, Op.EQUAL));   // a, c, d
        assertEquals(1, count(d, Op.REMOVED)); // b
        assertEquals(2, count(d, Op.ADDED));   // x, e
    }

    @Test
    public void lcsTableLength() {
        int[][] t = engine.lcsTable(Arrays.asList("A", "B", "C", "B", "D", "A", "B"),
                Arrays.asList("B", "D", "C", "A", "B", "A"));
        assertEquals(4, t[0][0]); // classic textbook example: LCS length 4
    }

    @Test
    public void emptyVersusContent() {
        List<DiffLine> d = engine.diff(new ArrayList<>(), Arrays.asList("x", "y"));
        assertEquals(2, count(d, Op.ADDED));
        String out = engine.describeFileDiff("f.txt", null, "x\ny\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(out.contains("ADDED: f.txt"));
        assertTrue(out.contains("+ x"));
    }

    @Test
    public void lineNumbersAreCorrect() {
        List<DiffLine> d = engine.diff(Arrays.asList("a", "b", "c"), Arrays.asList("a", "c"));
        DiffLine removed = d.stream().filter(l -> l.op() == Op.REMOVED).findFirst().orElseThrow();
        assertEquals("b", removed.text());
        assertEquals(2, removed.oldLine());
    }

    @Test
    public void binaryDetection() {
        assertTrue(DiffEngine.isProbablyText("héllo wörld".getBytes(StandardCharsets.UTF_8)));
        assertFalse(DiffEngine.isProbablyText(new byte[] {1, 2, 0, 3}));
        assertFalse(DiffEngine.isProbablyText(new byte[] {(byte) 0xC3, (byte) 0x28})); // invalid UTF-8
        String out = engine.describeFileDiff("img.png", new byte[] {0, 1}, new byte[] {0, 2, 3});
        assertTrue(out.contains("Binary file"));
    }

    @Test
    public void lineEndingsOnly() {
        String out = engine.describeFileDiff("w.txt", "a\r\nb\r\n".getBytes(StandardCharsets.UTF_8),
                "a\nb\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(out.contains("line endings"));
    }

    @Test
    public void bigFileFallsBackWithoutCrashing() {
        // 3000 x 3000 different middle lines = 9 million cells > limit -> remove/add block
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            a.add("old " + i);
            b.add("new " + i);
        }
        List<DiffLine> d = engine.diff(a, b);
        assertEquals(3000, count(d, Op.REMOVED));
        assertEquals(3000, count(d, Op.ADDED));
    }

    @Test
    public void splitLinesHandlesTrailingNewline() {
        assertEquals(Arrays.asList("a", "b"), DiffEngine.splitLines("a\nb\n"));
        assertEquals(Arrays.asList("a", "b"), DiffEngine.splitLines("a\r\nb"));
        assertEquals(0, DiffEngine.splitLines("").size());
    }
}
