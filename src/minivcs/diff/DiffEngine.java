/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.diff;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Tamia
 * 
 * compares two versions of a file line by line using the longest common subsequence algorithm
 *
 * lines that appear in both versions in the same order are unchanged
 * other old lines are removed and other new lines are added
 *
 * limits the amount of memory and processing used for large files
 * checks for matching lines at the start and end before comparing the middle
 * uses a simpler comparison when the middle section is too large
 * skips line comparisons for files larger than the size limit
 *
 * only treats content as text if it contains no null bytes and is valid UTF-8
 * otherwise reports that the files are binary
 */
public class DiffEngine {

    /** limits the comparison table to about 16 MB of memory */
    public static final long MAX_TABLE_CELLS = 4_000_000L;
    /** files larger than 5 MB are not compared line by line */
    public static final long MAX_TEXT_BYTES = 5L * 1024 * 1024;
    private static final int CONTEXT_LINES = 3;

    public enum Op { EQUAL, ADDED, REMOVED }

    /** stores one line of the comparison and its line numbers */
    public record DiffLine(Op op, String text, int oldLine, int newLine) { }

    /** compares two lists of lines and returns their differences */
    public List<DiffLine> diff(List<String> a, List<String> b) {
        List<DiffLine> result = new ArrayList<>();

        // find matching lines at the start
        int start = 0;
        while (start < a.size() && start < b.size() && a.get(start).equals(b.get(start))) {
            result.add(new DiffLine(Op.EQUAL, a.get(start), start + 1, start + 1));
            start++;
        }
        // find matching lines at the end
        int endA = a.size();
        int endB = b.size();
        while (endA > start && endB > start && a.get(endA - 1).equals(b.get(endB - 1))) {
            endA--;
            endB--;
        }
        List<String> midA = a.subList(start, endA);
        List<String> midB = b.subList(start, endB);

        // compare the middle using LCS or mark the whole section as removed and added
        if ((long) midA.size() * midB.size() <= MAX_TABLE_CELLS) {
            int[][] table = lcsTable(midA, midB);
            int i = 0;
            int j = 0;
            while (i < midA.size() && j < midB.size()) {
                if (midA.get(i).equals(midB.get(j))) {
                    result.add(new DiffLine(Op.EQUAL, midA.get(i), start + i + 1, start + j + 1));
                    i++;
                    j++;
                } else if (table[i + 1][j] >= table[i][j + 1]) {
                    result.add(new DiffLine(Op.REMOVED, midA.get(i), start + i + 1, 0));
                    i++;
                } else {
                    result.add(new DiffLine(Op.ADDED, midB.get(j), 0, start + j + 1));
                    j++;
                }
            }
            for (; i < midA.size(); i++) {
                result.add(new DiffLine(Op.REMOVED, midA.get(i), start + i + 1, 0));
            }
            for (; j < midB.size(); j++) {
                result.add(new DiffLine(Op.ADDED, midB.get(j), 0, start + j + 1));
            }
        } else {
            for (int i = 0; i < midA.size(); i++) {
                result.add(new DiffLine(Op.REMOVED, midA.get(i), start + i + 1, 0));
            }
            for (int j = 0; j < midB.size(); j++) {
                result.add(new DiffLine(Op.ADDED, midB.get(j), 0, start + j + 1));
            }
        }

        // add the matching lines from the end
        for (int k = 0; k < a.size() - endA; k++) {
            result.add(new DiffLine(Op.EQUAL, a.get(endA + k), endA + k + 1, endB + k + 1));
        }
        return result;
    }

    /**
     * builds a table that stores the longest common subsequence length for each position
     * fills the table from the bottom right corner
     */
    public int[][] lcsTable(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int[][] table = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a.get(i).equals(b.get(j))) {
                    table[i][j] = table[i + 1][j + 1] + 1;
                } else {
                    table[i][j] = Math.max(table[i + 1][j], table[i][j + 1]);
                }
            }
        }
        return table;
    }

    /**
     * creates readable comparison output for one file
     *
     * @param path the file path shown in the heading
     * @param oldBytes the old file content, or null if the file did not exist
     * @param newBytes the new file content, or null if the file no longer exists
     */
    public String describeFileDiff(String path, byte[] oldBytes, byte[] newBytes) {
        String kind = oldBytes == null ? "ADDED" : newBytes == null ? "DELETED" : "MODIFIED";
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(kind).append(": ").append(path).append(" ===\n");

        byte[] oldData = oldBytes == null ? new byte[0] : oldBytes;
        byte[] newData = newBytes == null ? new byte[0] : newBytes;

        if (!isProbablyText(oldData) || !isProbablyText(newData)) {
            sb.append("Binary file ").append(kind.toLowerCase())
              .append(" (old: ").append(sizeText(oldBytes)).append(", new: ").append(sizeText(newBytes))
              .append(") - line diff not shown.\n");
            return sb.toString();
        }
        List<String> oldLines = splitLines(new String(oldData, StandardCharsets.UTF_8));
        List<String> newLines = splitLines(new String(newData, StandardCharsets.UTF_8));
        List<DiffLine> lines = diff(oldLines, newLines);
        boolean anyChange = lines.stream().anyMatch(l -> l.op() != Op.EQUAL);
        if (!anyChange) {
            sb.append("(Text is identical line-by-line; only line endings or the final newline differ.)\n");
            return sb.toString();
        }
        sb.append(formatHunks(lines));
        return sb.toString();
    }

    /** shows changed lines with a few unchanged lines around them */
    String formatHunks(List<DiffLine> lines) {
        int size = lines.size();
        boolean[] show = new boolean[size];
        for (int i = 0; i < size; i++) {
            if (lines.get(i).op() != Op.EQUAL) {
                for (int k = Math.max(0, i - CONTEXT_LINES); k <= Math.min(size - 1, i + CONTEXT_LINES); k++) {
                    show[k] = true;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < size) {
            if (!show[i]) {
                i++;
                continue;
            }
            int end = i;
            while (end < size && show[end]) {
                end++;
            }
            sb.append(hunkHeader(lines.subList(i, end))).append('\n');
            for (int k = i; k < end; k++) {
                DiffLine l = lines.get(k);
                char prefix = l.op() == Op.ADDED ? '+' : l.op() == Op.REMOVED ? '-' : ' ';
                sb.append(prefix).append(' ').append(l.text()).append('\n');
            }
            i = end;
        }
        return sb.toString();
    }

    /** creates a header showing the old and new line ranges */
    private String hunkHeader(List<DiffLine> hunk) {
        int oldStart = 0;
        int newStart = 0;
        int oldCount = 0;
        int newCount = 0;
        for (DiffLine l : hunk) {
            if (l.op() != Op.ADDED) {
                if (oldStart == 0) {
                    oldStart = l.oldLine();
                }
                oldCount++;
            }
            if (l.op() != Op.REMOVED) {
                if (newStart == 0) {
                    newStart = l.newLine();
                }
                newCount++;
            }
        }
        return "@@ -" + oldStart + "," + oldCount + " +" + newStart + "," + newCount + " @@";
    }

    /** checks for null bytes and confirms that the content is valid UTF-8 */
    public static boolean isProbablyText(byte[] data) {
        int limit = Math.min(data.length, 8000);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return false;
            }
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /** splits text into lines and supports Windows and Unix line endings */
    public static List<String> splitLines(String text) {
        if (text.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = text.split("\r?\n", -1);
        List<String> lines = new ArrayList<>(Arrays.asList(parts));
        if (text.endsWith("\n")) {
            lines.remove(lines.size() - 1);  // a final newline does not create another line
        }
        return lines;
    }

    private static String sizeText(byte[] data) {
        return data == null ? "absent" : data.length + " bytes";
    }
}
