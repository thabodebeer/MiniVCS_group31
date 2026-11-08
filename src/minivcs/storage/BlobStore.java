/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import minivcs.core.VcsException;
import minivcs.util.HashUtil;

/**
 *
 * @author Tamia
 * 
 * stores file contents using their SHA-256 hashes
 *
 * extends ObjectStore to store byte arrays
 * uses streaming to handle large files without loading everything into memory
 *
 * checks file hashes when reading or restoring files
 * this helps detect damaged or changed stored files
 */
public class BlobStore extends ObjectStore<byte[]> {

    public BlobStore(Path objectsDir) {
        super(objectsDir);
    }

    @Override
    protected void write(byte[] obj, OutputStream out) throws IOException {
        out.write(obj);
    }

    @Override
    protected byte[] read(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    @Override
    protected void verify(String key, byte[] obj) {
        if (!HashUtil.sha256(obj).equals(key)) {
            throw new VcsException("Stored object " + HashUtil.shortHash(key)
                    + " is corrupt (its content no longer matches its hash).");
        }
    }

    /**
     * copies a file into storage while calculating its hash
     * saves the file using its hash as the name
     *
     * @return the SHA-256 hash of the stored file
     */
    public String store(Path file) throws IOException {
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, "incoming-", ".part");
        try {
            MessageDigest digest = HashUtil.newDigest();
            try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            String hash = HashUtil.toHex(digest.digest());
            Path target = pathFor(hash);
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                moveIntoPlace(temp, target);
            }
            return hash;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * restores a stored file to the working folder
     * checks the file hash before replacing the original file
     * uses a temporary file to prevent incomplete restores
     */
    public void restore(String hash, Path dest) throws IOException {
        Path source = pathFor(hash);
        if (!Files.isRegularFile(source)) {
            throw new VcsException("Stored object " + HashUtil.shortHash(hash)
                    + " is missing - cannot restore " + dest.getFileName());
        }
        Path parent = dest.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".minivcs-restore-", ".part");
        try {
            MessageDigest digest = HashUtil.newDigest();
            try (InputStream in = new DigestInputStream(Files.newInputStream(source), digest)) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!HashUtil.toHex(digest.digest()).equals(hash)) {
                throw new VcsException("Stored object " + HashUtil.shortHash(hash)
                        + " is corrupt - " + dest.getFileName() + " was NOT changed.");
            }
            moveIntoPlace(temp, dest);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** returns the size of a stored file in bytes */
    public long size(String hash) throws IOException {
        return Files.size(pathFor(hash));
    }

    /** counts the stored files */
    public long countObjects() throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().endsWith(".part"))
                    .count();
        }
    }
}

