/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.scan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 *
 * @author Tamia
 */
public class IgnoreRules {

     /** stores one ignore rule */
    private record Rule(String text, Pattern regex, boolean directoryOnly, boolean fullPath) { }

    private final List<Rule> patterns = new ArrayList<>();

     /** loads ignore rules from a file */
    public void load(Path ignoreFile) throws IOException {
        patterns.clear();
        if (!Files.isRegularFile(ignoreFile)) {
            return;
        }
        for (String line : Files.readAllLines(ignoreFile, StandardCharsets.UTF_8)) {
            addPattern(line);
        }
    }

    /** adds a rule and skips blank lines and comments */
    public void addPattern(String line) {
        String p = line.strip();
        if (p.isEmpty() || p.startsWith("#")) {
            return;
        }
        String original = p;
        boolean directoryOnly = p.endsWith("/");
        if (directoryOnly) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.isEmpty()) {
            return;
        }
        boolean fullPath = p.contains("/");
        patterns.add(new Rule(original, globToRegex(p), directoryOnly, fullPath));
    }

    /**
     * checks whether a path matches any ignore rule
     *
     * @param relativePath path from the repository root using /
     * @param isDirectory true if the path is a folder
     * @return true if a rule matches
     */
    public boolean matches(String relativePath, boolean isDirectory) {
        String name = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        for (Rule rule : patterns) {
            if (rule.directoryOnly() && !isDirectory) {
                continue;
            }
            String subject = rule.fullPath() ? relativePath : name;
            if (rule.regex().matcher(subject).matches()) {
                return true;
            }
        }
        return false;
    }

    /** returns the ignore rules for display */
    public List<String> getPatterns() {
        List<String> list = new ArrayList<>();
        for (Rule r : patterns) {
            list.add(r.text());
        }
        return Collections.unmodifiableList(list);
    }

    /** converts a wildcard pattern into a regular expression */
    static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }
}
