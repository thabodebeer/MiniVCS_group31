/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import minivcs.core.VcsException;
import minivcs.util.HashUtil;

/**
 *
 * @author Tamia
 * 
 * stores objects using the SHA-256 hash of their content as the key
 *
 * the type T allows different kinds of objects to be stored
 * subclasses decide how objects are converted to bytes and read back
 * BlobStore uses this class to store file contents
 *
 * objects are stored in subfolders based on the first two characters of the hash
 * this prevents too many files from being stored in one folder
 */
public abstract class ObjectStore<T> {

    /** folder where stored objects are kept */
    protected final Path dir;

    protected ObjectStore(Path dir) {
        this.dir = dir;
    }

    /** converts an object into bytes */
    protected abstract void write(T obj, OutputStream out) throws IOException;

    /** converts bytes back into an object */
    protected abstract T read(InputStream in) throws IOException;

    /** checks whether an object matches its key */
    protected void verify(String key, T obj) {
        // subclasses can add their own checks
    }

    /** saves an object using its key and avoids storing duplicates */
    public void put(String key, T obj) throws IOException {
        Path target = pathFor(key);
        verify(key, obj);
        if (Files.exists(target)) {
            return; // object is already stored
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "incoming-", ".part");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp))) {
                write(obj, out);
            }
            moveIntoPlace(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** loads an object and checks that it is valid */
    public T get(String key) throws IOException {
        Path file = pathFor(key);
        if (!Files.isRegularFile(file)) {
            throw new VcsException("Stored object " + HashUtil.shortHash(key)
                    + " is missing - the repository data is damaged.");
        }
        T obj;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            obj = read(in);
        }
        verify(key, obj);
        return obj;
    }

    /** checks whether an object exists in storage */
    public boolean exists(String key) {
        return Files.isRegularFile(pathFor(key));
    }

    /**
     * finds the storage path for an object
     * checks the key to prevent access outside the objects folder
     */
    protected Path pathFor(String key) {
        if (!HashUtil.isValidHash(key)) {
            throw new VcsException("Invalid object key: " + key);
        }
        return dir.resolve(key.substring(0, 2)).resolve(key.substring(2));
    }

    /** moves a temporary file to its final location */
    protected static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
