package edu.uob;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Database {
    private File database;
    private List<Table> tables;

    public Database(String pathname) throws MySQLException {
        this.database = new File(pathname);

        if (!this.database.exists() || !this.database.isDirectory()) {
            throw new MySQLException.DatabaseNotFoundException();
        }
        this.tables = new LinkedList<>();

        File[] files = this.database.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".tab")) {
                this.tables.add(new Table(this.database, file.getName()));
            }
        }
    }

    public File getDatabase() {
        return this.database;
    }

    public Iterable<Table> listTables() {
        return this.tables;
    }

    public void removeTable(String tableName) {
        for (Table table : this.tables) {
            if (table.getNameWithoutExtension().equalsIgnoreCase(tableName)) {
                this.tables.remove(table);
                break;
            }
        }
    }

    public boolean hasTable(String name) {
        for (Table table : this.tables) {
            if (table.getTableFile().getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public Table getTable(String name) {
        for (Table table : this.tables) {
            if (table.getTableFile().getName().equalsIgnoreCase(name)) {
                return table;
            }
        }
        return null;
    }

    public File getTableFile(String name) {
        for (Table table : this.tables) {
            if (table.getTableFile().getName().equalsIgnoreCase(name)) {
                return table.getTableFile();
            }
        }
        return null;
    }

    public void createNewTable(File table, String... attributes) throws MySQLException {
        if (hasTable(table.getName())) {
            throw new MySQLException("This table already exists, failed to create!");
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(table))) {
            // two rules:
            // 1. no duplicate attribute allowed
            Set<String> set = Arrays.stream(attributes).map(String::toLowerCase).collect(Collectors.toSet());
            if (set.size() != attributes.length) {
                throw new MySQLException("Duplicate attribute found, failed to create table!");
            }
            // 2. user cannot manually assign 'id' column
            if (set.contains("id")) {
                throw new MySQLException("You cannot manually assign the ID attribute!");
            }

            // write attributes into the file.
            bw.write("id");
            for (String attribute : attributes) {
                bw.write('\t' + attribute);
            }
            bw.newLine();
            bw.flush();
            bw.close();
            this.tables.add(new Table(this.database, table.getName()));
        } catch (IOException e) {
            throw new MySQLException.MyIOException("Failed to create table file.");
        }
    }

    // the received name must not end with ".tab" extension!
    public void createNewTable(String name, String... attributes) throws MySQLException {
        createNewTable(new File(this.database, name + ".tab"), attributes);
    }
}
