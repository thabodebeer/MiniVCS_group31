/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

/**
 *
 * @author Tamia
 * 
 * the main exception used to report errors to the user
 *
 * extends RuntimeException so the CLI and GUI can handle errors with one exception type
 * keeps the original cause when an error comes from another part of the program
 */
public class VcsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public VcsException(String message) {
        super(message);
    }

    public VcsException(String message, Throwable cause) {
        super(message, cause);
    }
}

