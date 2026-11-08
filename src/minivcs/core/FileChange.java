/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

/**
 *
 * @author Tamia
 * 
 * stores one change made to a file, such as adding, changing or deleting it
 * a record is a short way to create a class that stores data
 * java automatically creates the constructor and methods to compare and display the data
 */
public record FileChange(ChangeType type, String path) implements Comparable<FileChange> {

    @Override
    public int compareTo(FileChange other) {
        return path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return String.format("%-9s %s", type.getLabel() + ":", path);
    }
}
