/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package minivcs;

import minivcs.ui.CLIMenu;

/**
 *
 * @author Tamia
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println("Usage:");
            System.out.println("  java -jar MiniVCS-CLI-1.0.jar                               interactive text menu");
            System.out.println("  java -jar MiniVCS-CLI-1.0.jar [--repo folder] <command> [args]   one command");
            return;
        }
        CLIMenu.main(args);
    }
}
