package edu.uob;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class test {
    private String storageFolderPath;
    private Controller controller;


    public static void main(String[] args) {
        test server = new test();
        server.startConsole();
    }

    public test() {
        storageFolderPath = Paths.get("databases").toAbsolutePath().toString();
        try {
            Files.createDirectories(Paths.get(storageFolderPath));
        } catch(IOException ioe) {
            System.out.println("Failed to create database storage folder.");
        }
        controller = new Controller(storageFolderPath);
    }

    public String handleCommand(String command) {
        return controller.handleCommand(command);
    }

    private void startConsole() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("DBServer running. Type commands below:");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Server shutting down...");
                break;
            }
            System.out.println(handleCommand(input));
        }

        scanner.close();
    }

}
