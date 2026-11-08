/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

/**
 * @author Tamia
 * 
 * shows the three types of changes that can happen to a file
 */
public enum ChangeType {
    ADDED("A", "added"),
    MODIFIED("M", "modified"),
    DELETED("D", "deleted");

    private final String symbol;
    private final String label;

    ChangeType(String symbol, String label) {
        this.symbol = symbol;
        this.label = label;
    }

    public String getSymbol() { return symbol; }
    public String getLabel() { return label; }
}