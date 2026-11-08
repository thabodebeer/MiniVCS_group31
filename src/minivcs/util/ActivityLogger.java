/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


/**
 *
 * @author Tamia
 * 
 * saves each action or error with a date and time in activity.log
 *
 * records actions such as commits and rollbacks
 * also records errors when commands fail
 *
 * logging should not stop a command from running
 * if the log cannot be written, a warning is shown and the operation continues
 */
public class ActivityLogger {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path logFile;
    private boolean warned;

    public ActivityLogger(Path logFile) {
        this.logFile = logFile;
    }

    public void log(String action) {
        write("INFO ", action);
    }

    public void logError(String message) {
        write("ERROR", message);
    }

    /** prevents multiple threads from writing to the log at the same time */
    private synchronized void write(String level, String message) {
        String clean = message == null ? "" : message.replace('\r', ' ').replace('\n', ' ');
        String line = LocalDateTime.now().format(FORMAT) + " | " + level + " | " + clean + System.lineSeparator();
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (!warned) {
                System.err.println("Warning: could not write activity log (" + e.getMessage() + ")");
                warned = true;
            }
        }
    }

    /** returns the most recent log lines in their original order */
    public List<String> readRecent(int max) throws IOException {
        if (!Files.exists(logFile)) {
            return new ArrayList<>();
        }
        List<String> all = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        return new ArrayList<>(all.subList(Math.max(0, all.size() - max), all.size()));
    }

    public Path getLogFile() {
        return logFile;
    }
}
