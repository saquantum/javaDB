package edu.uob;

import java.io.File;
import java.util.*;

public class Controller {

    private String currentDatabase = null;
    private String currentTable = null;
    private String storageFolderPath;

    private enum Keywords {
        USE, CREATE, DROP, ALTER, INSERT, SELECT, UPDATE, DELETE, JOIN
    }

    private static Map<String, Keywords> typeKeywords = new HashMap();
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
        this.storageFolderPath = storageFolderPath;
    }

    public String handleCommand(String command) {
        String[] segments = Controller.lexTokens(command);
        if (!Controller.isValidCommand(segments)) {
            return "[ERROR]: invalid command.";
        }
        if (!";".equals(segments[segments.length - 1])) {
            return "[ERROR]: a SQL query must end with a semicolon!";
        }
        segments = Arrays.copyOf(segments, segments.length - 1);
        Keywords key = Controller.typeKeywords.get(segments[0].toUpperCase());
        String out;
        try {
            if (key == Keywords.USE) {
                out = this.handleUseCommand(segments);
            } else if (key == Keywords.CREATE) {
                out = this.handleCreateCommand(segments);
            } else {
                out = "[ERROR]: invalid command.";
            }
        } catch (MySQLException e) {
            out = "[ERROR]: " + e.getMessage();
        }
        return out;
    }

    private String handleCreateCommand(String[] segments) {
        if (segments.length < 3) {
            throw new MySQLException.InvalidQueryException("invalid usage of CREATE command.");
        }
        String out;
        if ("DATABASE".equalsIgnoreCase(segments[1])) {
            out = handleCreateDatabase(segments);
        } else if ("TABLE".equalsIgnoreCase(segments[1])) {
            out = handleCreateTable(segments);
        } else {
            throw new MySQLException.InvalidQueryException("invalid usage of CREATE command.");
        }
        return out;
    }

    private String handleCreateDatabase(String[] segments) {
        if (segments.length != 3) {
            throw new MySQLException.InvalidQueryException("invalid usage of CREATE command.");
        }
        File db = new File(new File(this.storageFolderPath), segments[2].toLowerCase());
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

    private String handleCreateTable(String[] segments) {

        return "[OK]";
    }

    private String handleUseCommand(String[] segments) {
        if (segments.length != 2) {
            throw new MySQLException.InvalidQueryException("invalid usage of USE command.");
        }
        File root = new File(this.storageFolderPath);
        File[] files = root.listFiles();
        for (File file : files) {
            if (file.isDirectory() && file.getName().equals(segments[1])) {
                this.currentDatabase = file.getName();
                return "[OK]";
            }
        }
        throw new MySQLException("This database does not exist!");
    }

    public static boolean isValidCommand(String[] segments) {
        return Controller.typeKeywords.containsKey(segments[0].toUpperCase());
    }

    public static String[] lexTokens(String command) {
        List<String> segments = new LinkedList<>();
        command = command.trim();
        while (command.length() > 0) {
            String[] tmp;
            if (command.charAt(0) == '\'') {
                tmp = Controller.handleStringLiteral(command);
            } else if (Character.isDigit(command.charAt(0))) {
                tmp = Controller.handleNumberLiteral(command);
                try {
                    Double.parseDouble(tmp[0]);
                } catch (NumberFormatException e) {
                    throw new MySQLException.InvalidQueryException("invalid number format");
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
                throw new MySQLException.InvalidQueryException("invalid character found.");
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
    private static String[] handleStringLiteral(String command) {
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
