/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.ui;

import java.util.ArrayList;
import java.util.List;
import minivcs.core.VcsException;

/**
 *
 * @author Tamia
 * 
 * splits a command line into words while keeping quoted text together
 *
 * allows commands with spaces in messages and file paths
 */
public final class CommandParser {

    private CommandParser() {
    }

    public static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        if (line == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;          // the quote character we are inside, or 0
        boolean inToken = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inToken = true;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (quote != 0) {
            throw new VcsException("Missing closing quote (" + quote + ")");
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
