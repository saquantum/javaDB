package edu.uob;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Controller {

    private Database currentDatabase = null;
    private File storageFolderPath;

    private enum Keywords {
        USE, CREATE, DROP, ALTER, INSERT, SELECT, UPDATE, DELETE, JOIN
    }

    private static Map<String, Keywords> typeKeywords = new HashMap<>();
    private static Set<Character> validChars = new HashSet<>();

    static {
        Controller.typeKeywords.put("USE", Keywords.USE);
        Controller.typeKeywords.put("CREATE", Keywords.CREATE);
        Controller.typeKeywords.put("DROP", Keywords.DROP);
        Controller.typeKeywords.put("ALTER", Keywords.ALTER);
        Controller.typeKeywords.put("INSERT", Keywords.INSERT);
        Controller.typeKeywords.put("SELECT", Keywords.SELECT);
        Controller.typeKeywords.put("UPDATE", Keywords.UPDATE);
        Controller.typeKeywords.put("DELETE", Keywords.DELETE);
        Controller.typeKeywords.put("JOIN", Keywords.JOIN);
        Collections.addAll(Controller.validChars, '!', '#', '$', '%', '&', '(', ')', '*', '+', ',', '-', '.', '/', ':', ';', '>', '=', '<', '?', '@', '[', '\\', ']', '^', '_', '`', '{', '}', '~');
    }

    public Controller(String storageFolderPath) {
        this.storageFolderPath = new File(storageFolderPath);
    }

    public String handleCommand(String command) {
        String[] segments;
        try {
            segments = Controller.lexTokens(command);
        } catch (MySQLException e) {
            return "[ERROR]: " + e.getMessage();
        }
        if (!Controller.isValidCommand(segments)) {
            return "[ERROR]: Unknown type of command.";
        }
        if (!";".equals(segments[segments.length - 1])) {
            return "[ERROR]: A SQL statement must end with a semicolon!";
        }
        segments = Arrays.copyOf(segments, segments.length - 1);
        Keywords key = Controller.typeKeywords.get(segments[0].toUpperCase());
        String out;
        try {
            if (key == Keywords.USE) {
                out = this.handleUseCommand(segments);
            } else if (key == Keywords.CREATE) {
                out = this.handleCreateCommand(segments);
            } else if (key == Keywords.DROP) {
                out = this.handleDropCommand(segments);
            } else if (key == Keywords.ALTER) {
                out = this.handleAlterCommand(segments);
            } else if (key == Keywords.INSERT) {
                out = this.handleInsertCommand(segments);
            } else if (key == Keywords.SELECT) {
                out = this.handleSelectCommand(segments);
            } else if (key == Keywords.UPDATE) {
                out = this.handleUpdateCommand(segments);
            } else if (key == Keywords.DELETE) {
                out = this.handleDeleteCommand(segments);
            } else if (key == Keywords.JOIN) {
                out = this.handleJoinCommand(segments);
            } else {
                out = "[ERROR]: Invalid type of command.";
            }
        } catch (MySQLException e) {
            out = "[ERROR]: " + e.getMessage();
        }
        return out;
    }

    private String handleJoinCommand(String[] segments) throws MySQLException {
        return "[ERROR]";
    }

    private String handleDeleteCommand(String[] segments) throws MySQLException {
        return "[ERROR]";
    }

    private String handleUpdateCommand(String[] segments) throws MySQLException {
        return "[ERROR]";
    }

    private String handleSelectCommand(String[] segments) throws MySQLException {
        return "[ERROR]";
    }

    private String handleInsertCommand(String[] segments) throws MySQLException {
        if (segments.length < 6) {
            throw new MySQLException.InvalidQueryException("Invalid usage of INSERT command.");
        }

        if (currentDatabase == null) {
            throw new MySQLException("You have not selected a database yet!");
        }

        if (!"INTO".equalsIgnoreCase(segments[1])) {
            throw new MySQLException.InvalidQueryException("Missing INTO keyword.");
        }

        Table table = this.currentDatabase.getTable(segments[2].toLowerCase() + ".tab");

        if (!"VALUES".equalsIgnoreCase(segments[3])) {
            throw new MySQLException.InvalidQueryException("Missing VALUES keyword.");
        }

        return handleValueList(table, segments);
    }

    private String handleValueList(Table table, String[] segments) throws MySQLException {
        if (!"(".equals(segments[4])) {
            throw new MySQLException.InvalidQueryException("Missing left parenthesis for the value list!");
        }
        List<String> values = new ArrayList<>();
        int index = 5;
        while (index < segments.length) {
            // a value
            if (!isValidValue(segments[index])) {
                throw new MySQLException.InvalidQueryException("Invalid value: " + segments[index]);
            }
            values.add(segments[index]);
            // followed by a comma or right parenthesis
            if (",".equals(segments[index + 1])) {
                index += 2;
            } else if (")".equals(segments[index + 1])) {
                if (index + 1 == segments.length - 1) {
                    table.appendRow(values);
                    return "[OK]";
                } else {
                    throw new MySQLException.InvalidQueryException("Unknown command found after the value list!");
                }
            } else {
                throw new MySQLException.InvalidQueryException("Missing comma in the value list!");
            }
        }
        throw new MySQLException.InvalidQueryException("Missing right parenthesis for the value list!");
    }

    private boolean isValidValue(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return "TRUE".equals(str) || "FALSE".equals(str) || "NULL".equals(str) || (str.startsWith("'") && str.endsWith("'"));
        }
    }

    private String handleAlterCommand(String[] segments) throws MySQLException {
        if (segments.length != 5) {
            throw new MySQLException.InvalidQueryException("Invalid usage of ALTER command.");
        }

        if (currentDatabase == null) {
            throw new MySQLException("You have not selected a database yet!");
        }

        if (!"TABLE".equalsIgnoreCase(segments[1])) {
            throw new MySQLException.InvalidQueryException("Missing TABLE keyword.");
        }

        Table table = this.currentDatabase.getTable(segments[2].toLowerCase() + ".tab");

        if (!"ADD".equalsIgnoreCase(segments[3]) && !"DROP".equalsIgnoreCase(segments[3])) {
            throw new MySQLException.InvalidQueryException("Missing ADD or DROP keyword, or invalid type of alteration.");
        }

        if ("ADD".equalsIgnoreCase(segments[3])) {
            table.addNewColumn(segments[4]);
            return "[OK]";
        }
        if ("DROP".equalsIgnoreCase(segments[3])) {
            table.dropColumn(segments[4]);
            return "[OK]";
        }

        throw new MySQLException("The attribute you would like to alter does not exist!");
    }


    private String handleDropCommand(String[] segments) throws MySQLException {
        if (segments.length != 3) {
            throw new MySQLException.InvalidQueryException("Invalid usage of DROP command.");
        }

        if ("DATABASE".equalsIgnoreCase(segments[1])) {
            File db = new File(this.storageFolderPath, segments[2].toLowerCase());
            if (!db.exists()) {
                throw new MySQLException("The database you would like to drop does not exist!");
            } else {
                if (db.delete()) {
                    return "[OK]";
                } else {
                    throw new MySQLException("Failed to drop the database!");
                }
            }

        } else if ("TABLE".equalsIgnoreCase(segments[1])) {
            if (currentDatabase == null) {
                throw new MySQLException("You have not selected a database yet!");
            }
            File table = this.currentDatabase.getTableFile(segments[2].toLowerCase() + ".tab");
            if (!table.exists()) {
                throw new MySQLException("The table you would like to drop does not exist!");
            } else {
                if (table.delete()) {
                    return "[OK]";
                } else {
                    throw new MySQLException("Failed to drop the table!");
                }
            }
        } else {
            throw new MySQLException.InvalidQueryException("Invalid usage of DROP command.");
        }
    }

    private String handleCreateCommand(String[] segments) throws MySQLException {
        if (segments.length < 3) {
            throw new MySQLException.InvalidQueryException("Invalid usage of CREATE command.");
        }
        String out;
        if ("DATABASE".equalsIgnoreCase(segments[1])) {
            out = handleCreateDatabase(segments);
        } else if ("TABLE".equalsIgnoreCase(segments[1])) {
            out = handleCreateTable(segments);
        } else {
            throw new MySQLException.InvalidQueryException("Invalid usage of CREATE command.");
        }
        return out;
    }

    private String handleCreateDatabase(String[] segments) throws MySQLException {
        if (segments.length != 3) {
            throw new MySQLException.InvalidQueryException("Invalid usage of CREATE command.");
        }
        File db = new File(this.storageFolderPath, segments[2].toLowerCase().toLowerCase());
        if (db.exists()) {
            throw new MySQLException("This database already exists, failed to create!");
        } else {
            if (db.mkdir()) {
                return "[OK]";
            } else {
                throw new MySQLException("Failed to create database.");
            }
        }
    }

    private String handleCreateTable(String[] segments) throws MySQLException {
        if (currentDatabase == null) {
            throw new MySQLException("You have not selected a database yet!");
        }

        File table = this.currentDatabase.getTableFile(segments[2].toLowerCase());
        if (table != null) {
            throw new MySQLException("This table already exists, failed to create!");
        }

        if (segments.length == 3) {
            this.currentDatabase.createNewTable(segments[2].toLowerCase());
            return "[OK]";
        } else {
            return handleAttributeList(segments);
        }
    }

    private String handleAttributeList(String[] segments) throws MySQLException {
        if (!"(".equals(segments[3])) {
            throw new MySQLException.InvalidQueryException("Missing left parenthesis for the attribute list!");
        }
        ArrayList<String> attributes = new ArrayList<>();
        int index = 4;
        while (index < segments.length) {
            // a plain text
            if (!isPlainText(segments[index])) {
                throw new MySQLException.InvalidQueryException("Invalid attribute naming: " + segments[index]);
            }
            attributes.add(segments[index]);
            // followed by a comma or right parenthesis
            if (",".equals(segments[index + 1])) {
                index += 2;
            } else if (")".equals(segments[index + 1])) {
                if (index + 1 == segments.length - 1) {
                    this.currentDatabase.createNewTable(segments[2].toLowerCase(), attributes.toArray(String[]::new));
                    return "[OK]";
                } else {
                    throw new MySQLException.InvalidQueryException("Unknown command found after the attribute list!");
                }
            } else {
                throw new MySQLException.InvalidQueryException("Missing comma in the attribute list!");
            }
        }
        throw new MySQLException.InvalidQueryException("Missing right parenthesis for the attribute list!");
    }

    private String handleUseCommand(String[] segments) throws MySQLException {
        if (segments.length != 2) {
            throw new MySQLException.InvalidQueryException("Invalid usage of USE command.");
        }
        File[] files = this.storageFolderPath.listFiles();
        if (files == null) {
            throw new MySQLException("Storage Folder Path does not exist!");
        }
        for (File file : files) {
            if (file.isDirectory() && file.getName().equalsIgnoreCase(segments[1])) {
                this.currentDatabase = new Database(file.getAbsolutePath());
                return "[OK]";
            }
        }
        throw new MySQLException("This database does not exist!");
    }

    private boolean isPlainText(String str) {
        if (str.length() == 0) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isLetterOrDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidCommand(String[] segments) {
        return Controller.typeKeywords.containsKey(segments[0].toUpperCase());
    }

    public static String[] lexTokens(String command) throws MySQLException {
        List<String> segments = new LinkedList<>();
        command = command.trim();
        while (!command.isEmpty()) {
            String[] tmp;
            if (command.charAt(0) == '\'') {
                tmp = Controller.handleStringLiteral(command);
            } else if (Character.isDigit(command.charAt(0))) {
                tmp = Controller.handleNumberLiteral(command);
                try {
                    Double.parseDouble(tmp[0]);
                } catch (NumberFormatException e) {
                    throw new MySQLException.InvalidQueryException("Invalid number format.");
                }
            } else if (Character.isAlphabetic(command.charAt(0))) {
                tmp = Controller.handleWholeWord(command);
            } else if (Controller.isValidSymbol(command.charAt(0))) {
                if (command.length() == 1) {
                    tmp = new String[]{command};
                } else {
                    tmp = new String[2];
                    tmp[0] = command.substring(0, 1);
                    tmp[1] = command.substring(1);
                }
            } else {
                throw new MySQLException.InvalidQueryException("Invalid character found.");
            }
            segments.add(tmp[0]);
            if (tmp.length == 1) {
                break;
            } else {
                command = tmp[1].trim();
            }
        }
        combineNumberAndOperator(segments);
        return segments.toArray(String[]::new);
    }

    // return 1~2 strings [word, ?restCommand]
    private static String[] handleWholeWord(String command) {
        command = command.trim();
        int index = command.length();
        for (int i = 0; i < command.length(); i++) {
            if (!Character.isLetterOrDigit(command.charAt(i)) && command.charAt(i) != '_') {
                index = i;
                break;
            }
        }
        if (index < command.length()) {
            return new String[]{command.substring(0, index), command.substring(index)};
        } else {
            return new String[]{command};
        }
    }

    // return 1~2 strings [stringLiteral, ?restCommand]
    // command must start with a '
    private static String[] handleStringLiteral(String command) throws MySQLException {
        int index = command.length();
        for (int i = 1; i < command.length(); i++) {
            if (command.charAt(i) == '\'') {
                index = i;
                break;
            }
        }
        if (index == command.length()) {
            throw new MySQLException.InvalidQueryException("SQL command does not have a concluding ': " + command);
        }
        if (index < command.length() - 1) {
            return new String[]{command.substring(0, index + 1), command.substring(index + 1)};
        } else {
            return new String[]{command};
        }
    }

    private static String[] handleNumberLiteral(String command) {
        command = command.trim();
        int index = command.length();
        for (int i = 0; i < command.length(); i++) {
            if (!Character.isDigit(command.charAt(i)) && command.charAt(i) != '.') {
                index = i;
                break;
            }
        }
        if (index < command.length()) {
            return new String[]{command.substring(0, index), command.substring(index)};
        } else {
            return new String[]{command};
        }
    }

    private static boolean isValidSymbol(char c) {
        return Controller.validChars.contains(c);
    }

    private static void combineNumberAndOperator(List<String> segments) {
        int i = 0;
        while (i < segments.size()) {
            if ("=".equals(segments.get(i)) || "<".equals(segments.get(i)) || ">".equals(segments.get(i)) || "!".equals(segments.get(i))) {
                if (i + 1 < segments.size() && "=".equals(segments.get(i + 1))) {
                    segments.add(i, segments.get(i) + segments.get(i + 1));
                    segments.remove(i + 1);
                    segments.remove(i + 1);
                }
            } else if ("-".equals(segments.get(i)) || "+".equals(segments.get(i))) {
                if (Character.isDigit(segments.get(i + 1).charAt(0))) {
                    segments.add(i, segments.get(i) + segments.get(i + 1));
                    segments.remove(i + 1);
                    segments.remove(i + 1);
                }
            }
            i++;
        }
    }


}
