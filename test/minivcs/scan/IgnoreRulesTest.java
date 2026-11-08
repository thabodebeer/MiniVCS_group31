package minivcs.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IgnoreRulesTest {

    private IgnoreRules rules(String... lines) {
        IgnoreRules r = new IgnoreRules();
        for (String l : lines) {
            r.addPattern(l);
        }
        return r;
    }

    @Test
    public void commentsAndBlankLinesAreSkipped() {
        IgnoreRules r = rules("# a comment", "", "   ");
        assertEquals(0, r.getPatterns().size());
    }

    @Test
    public void wildcardMatchesNameAnywhere() {
        IgnoreRules r = rules("*.log");
        assertTrue(r.matches("app.log", false));
        assertTrue(r.matches("src/deep/server.log", false));
        assertFalse(r.matches("app.log.txt", false));
    }

    @Test
    public void exactNameAndQuestionMark() {
        IgnoreRules r = rules("secret.txt", "temp?.txt");
        assertTrue(r.matches("a/secret.txt", false));
        assertTrue(r.matches("temp1.txt", false));
        assertFalse(r.matches("temp12.txt", false));
    }

    @Test
    public void trailingSlashMeansFoldersOnly() {
        IgnoreRules r = rules("build/");
        assertTrue(r.matches("build", true));
        assertTrue(r.matches("sub/build", true));
        assertFalse(r.matches("build", false));
    }

    @Test
    public void patternWithSlashIsMatchedFromRoot() {
        IgnoreRules r = rules("docs/*.tmp", "/root-only.txt");
        assertTrue(r.matches("docs/a.tmp", false));
        assertFalse(r.matches("other/docs/a.tmp", false));
        assertFalse(r.matches("docs/sub/a.tmp", false)); // '*' does not cross '/'
        assertTrue(r.matches("root-only.txt", false));
    }

    @Test
    public void regexCharactersAreLiteral() {
        IgnoreRules r = rules("a+b(1).txt");
        assertTrue(r.matches("a+b(1).txt", false));
        assertFalse(r.matches("aab1.txt", false));
    }
}
