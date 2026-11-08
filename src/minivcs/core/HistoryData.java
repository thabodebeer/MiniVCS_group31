/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package minivcs.core;

import java.io.Serializable;
import java.util.ArrayList;

/**
 *
 * @author Tamia
 * stores the commit history data in the history.ser file
 *
 * it also includes a version number to help the program recognise different history file formats
 * and this class is only used by HistoryManager
 */
final class HistoryData implements Serializable {

    private static final long serialVersionUID = 1L;

    final int formatVersion;
    final ArrayList<Commit> commits;
    final String head;

    HistoryData(int formatVersion, ArrayList<Commit> commits, String head) {
        this.formatVersion = formatVersion;
        this.commits = commits;
        this.head = head;
    }
}