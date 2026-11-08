/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 *
 * @author Tamia
 * 
 * provides helper methods for calculating SHA-256 hashes
 * uses the built-in Java MessageDigest class
 *
 * a SHA-256 hash is a 64-character fingerprint of data
 * the same content produces the same hash
 * changing the content produces a different hash
 * MiniVCS uses hashes to identify files and avoid storing duplicates
 *
 * files are read in small chunks to handle large files without using too much memory
 */
public final class HashUtil {

    private static final int BUFFER_SIZE = 8192;
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private HashUtil() {
        // prevents objects from being created
    }

    /** creates a new SHA-256 digest */
    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JDK", e);
        }
    }

    /** calculates the hash of a byte array */
    public static String sha256(byte[] data) {
        return toHex(newDigest().digest(data));
    }

    /** calculates the hash of a text string using UTF-8 */
    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /** calculates the hash of a file by reading it in small chunks */
    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return sha256(in);
        }
    }

    /** calculates the hash of the remaining data in a stream */
    public static String sha256(InputStream in) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return toHex(digest.digest());
    }

    /** converts hash bytes into lowercase hexadecimal text */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** checks whether a string is a valid SHA-256 hash */
    public static boolean isValidHash(String text) {
        return text != null && HASH_PATTERN.matcher(text).matches();
    }

    /** returns the first eight characters of a hash */
    public static String shortHash(String hash) {
        return hash == null ? "--------" : hash.substring(0, Math.min(8, hash.length()));
    }
}

