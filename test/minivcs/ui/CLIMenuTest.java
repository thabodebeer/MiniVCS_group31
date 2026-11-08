package minivcs.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Drives the interactive CLI with a scripted input, exactly like a user typing. */
public class CLIMenuTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void tokenizerHandlesQuotes() {
        assertEquals(List.of("add", "My Docs/a b.txt"), CommandParser.tokenize("add \"My Docs/a b.txt\""));
        assertEquals(List.of("diff", "abcd", "ef01"), CommandParser.tokenize("  diff   abcd ef01 "));
    }

    @Test
    public void fullSessionThroughTheMenu() throws Exception {
        Path project = tmp.newFolder("demo").toPath();
        Files.writeString(project.resolve("notes.txt"), "first line\n");

        String script = String.join("\n",
                project.toString(),            // folder question
                "status",                      // before init -> error
                "init",
                "add .",
                "commit Tamia's first commit", // apostrophe without quotes
                "log",
                "unknowncmd",
                "exit") + "\n";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CLIMenu menu = new CLIMenu(null, new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        menu.start(null);
        String out = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(out, out.contains("not a repository yet"));
        assertTrue(out, out.contains("Initialised empty MiniVCS repository"));
        assertTrue(out, out.contains("staged added:"));
        assertTrue(out, out.contains("Committed"));
        assertTrue(out, out.contains("Tamia's first commit"));
        assertTrue(out, out.contains("Unknown command"));
        assertTrue(out, out.contains("Goodbye."));
    }

    @Test
    public void rollbackAsksForConfirmation() throws Exception {
        Path project = tmp.newFolder("demo2").toPath();
        Files.writeString(project.resolve("a.txt"), "v1");
        String script = String.join("\n",
                project.toString(), "init", "add .", "commit v1",
                "exit") + "\n";
        CLIMenu setup = new CLIMenu(null, new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        setup.start(null);
        Files.writeString(project.resolve("a.txt"), "v2 local edit");

        String script2 = String.join("\n", project.toString(), "rollback HEAD", "no", "exit") + "\n";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new CLIMenu(null, new ByteArrayInputStream(script2.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(buffer, true, StandardCharsets.UTF_8)).start(null);
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out, out.contains("cancelled"));
        assertEquals("v2 local edit", Files.readString(project.resolve("a.txt")));
    }
}
