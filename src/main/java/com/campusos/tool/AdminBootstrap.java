package com.campusos.tool;

import com.campusos.model.User;
import com.campusos.service.BootstrapService;

import java.util.Scanner;

/**
 * Secure first-administrator bootstrap (run once after applying the schema).
 *
 * Usage:
 *   mvn compile exec:java -Dexec.mainClass=com.campusos.tool.AdminBootstrap \
 *        -Dexec.args="--name 'System Administrator' --email admin@campus.edu --password 'Admin@123'"
 *
 * Or run without arguments for an interactive prompt. The tool refuses to
 * run once any administrator exists, and no default password is ever shipped.
 */
public class AdminBootstrap {

    public static void main(String[] args) {
        System.out.println("=====================================================");
        System.out.println("  CampusOS - Administrator bootstrap");
        System.out.println("=====================================================");
        System.out.println("Creates the FIRST administrator account. Refuses if");
        System.out.println("an administrator already exists.\n");

        String name = null;
        String email = null;
        String password = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--name" -> name = nextArg(args, ++i, "name");
                case "--email" -> email = nextArg(args, ++i, "email");
                case "--password" -> password = nextArg(args, ++i, "password");
                default -> { }
            }
        }

        if (name == null || email == null || password == null) {
            System.out.println("Administrator details (input is hidden for passwords where supported):");
            Scanner scanner = new Scanner(System.in);
            if (name == null) {
                System.out.print("  Full name : ");
                name = scanner.nextLine().trim();
            }
            if (email == null) {
                System.out.print("  Email     : ");
                email = scanner.nextLine().trim();
            }
            if (password == null) {
                System.out.print("  Password  : ");
                password = scanner.nextLine();
            }
        }

        try {
            User admin = new BootstrapService().createFirstAdmin(name, email, password);
            System.out.println();
            System.out.println("SUCCESS - administrator account created:");
            System.out.println("  ID    : " + admin.getId());
            System.out.println("  Name  : " + admin.getFullName());
            System.out.println("  Email : " + admin.getEmail());
            System.out.println();
            System.out.println("You can now sign in to CampusOS with these credentials.");
        } catch (Exception e) {
            System.err.println();
            System.err.println("BOOTSTRAP FAILED: " + e.getMessage());
            System.err.println("Hints: check config/db.properties, MySQL connectivity,");
            System.err.println("       and that database/schema.sql was applied.");
            System.exit(1);
        }
    }

    private static String nextArg(String[] args, int index, String what) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for --" + what);
        }
        return args[index];
    }
}
