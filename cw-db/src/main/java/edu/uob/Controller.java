package edu.uob;

import java.io.*;
import java.util.*;

public class Controller {

    private Database currentDatabase = null;
    private final File storageFolderPath;

    private enum Keywords {
        USE, CREATE, DROP, ALTER, INSERT, SELECT, UPDATE, DELETE, JOIN
    }

    private static final Map<String, Keywords> typeKeywords = new HashMap<>();

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
    }

    public Controller(String storageFolderPath) {
        this.storageFolderPath = new File(storageFolderPath);
    }

    public String handleCommand(String command) {
        String[] segments;
        try {
            segments = Lexer.lexTokens(command);
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
        if (segments.length != 8) {
            throw new MySQLException.InvalidQueryException("Invalid usage of JOIN command.");
        }

        if (currentDatabase == null) {
            throw new MySQLException("You have not selected a database yet!");
        }

        if (!"AND".equalsIgnoreCase(segments[2])) {
            throw new MySQLException.InvalidQueryException("Missing AND keyword.");
        }

        if (!"ON".equalsIgnoreCase(segments[4])) {
            throw new MySQLException.InvalidQueryException("Missing ON keyword.");
        }

        if (!"AND".equalsIgnoreCase(segments[6])) {
            throw new MySQLException.InvalidQueryException("Missing AND keyword.");
        }

        Table table1 = this.currentDatabase.getTable(segments[1].toLowerCase() + ".tab");
        Table table2 = this.currentDatabase.getTable(segments[3].toLowerCase() + ".tab");
        if (table1 == null || table2 == null) {
            throw new MySQLException.InvalidQueryException("The tables you would like to JOIN do not exist!");
        }

        return Table.joinTables(table1, table2, segments);
    }

    private String handleDeleteCommand(String[] segments) throws MySQLException {
        if (segments.length < 5) {
            throw new MySQLException.InvalidQueryException("Invalid usage of DELETE command.");
        }

        if (currentDatabase == null) {
            throw new MySQLException("You have not selected a database yet!");
        }

        if (!"FROM".equalsIgnoreCase(segments[1])) {
            throw new MySQLException.InvalidQueryException("Missing FROM keyword.");
        }

        Table table = this.currentDatabase.getTable(segments[2].toLowerCase() + ".tab");
        if (table == null) {
            throw new MySQLException.InvalidQueryException("The table you would like to DELETE does not exist!");
        }

        if (!"WHERE".equalsIgnoreCase(segments[3])) {
            throw new MySQLException.InvalidQueryException("Missing WHERE keyword.");
        }
        int where = 3;
        ConditionParser cp = new ConditionParser(table, Arrays.copyOfRange(segments, where + 1, segments.length));
        table.updateSelectedRows(null, cp);
        return "[OK]";
    }

    private String handleUpdateCommand(String[] segments) throws MySQLException {
        if (segments.length < 7) {
            throw new MySQLException.InvalidQueryException("Invalid usage of UPDATE command.");
        }
        checkCurrentDatabase();

        if (!"SET".equalsIgnoreCase(segments[2])) {
            throw new MySQLException.InvalidQueryException("Missing SET keyword.");
        }

        Table table = this.currentDatabase.getTable(segments[1].toLowerCase() + ".tab");
        if (table == null) {
            throw new MySQLException.InvalidQueryException("The table you would like to UPDATE does not exist!");
        }

        Map<Integer, String> map = new HashMap<>();

        int index = 3;
        while (index < segments.length) {
            // an attribute
            if (!Controller.isPlainText(segments[index])) {
                throw new MySQLException.InvalidQueryException("Attribute " + segments[index] + " is not a valid attribute!");
            }
            int key = table.getAttributeIndex(segments[index]);
            index++;
            // followed by an equality
            if (index >= segments.length || !"=".equals(segments[index])) {
                throw new MySQLException.InvalidQueryException();
            }
            index++;
            // followed by a value
            if (index >= segments.length || !Controller.isValidValue(segments[index])) {
                throw new MySQLException.InvalidQueryException();
            }
            map.put(key, segments[index]);
            index++;
            // followed by a WHERE
            if (index < segments.length && "WHERE".equalsIgnoreCase(segments[index])) {
                break;
            }
        }

        // index points to WHERE
        int where = index;
        ConditionParser cp = null;
        if (where < segments.length) {
            cp = new ConditionParser(table, Arrays.copyOfRange(segments, where + 1, segments.length));
        }
        table.updateSelectedRows(map, cp);
        return "[OK]";
    }

    private String handleSelectCommand(String[] segments) throws MySQLException {
        if (segments.length < 4) {
            throw new MySQLException.InvalidQueryException("Invalid usage of SELECT command.");
        }
        checkCurrentDatabase();

        // find the selected attributes and the table.
        List<String> attributes = new ArrayList<>();
        Table table;
        int where = -1;

        // 1. wildcard case.
        if ("*".equals(segments[1]) && "FROM".equalsIgnoreCase(segments[2])) {
            attributes.add("*");
            table = this.currentDatabase.getTable(segments[3].toLowerCase() + ".tab");
            if (table == null) {
                throw new MySQLException.InvalidQueryException("The table you would like to SELECT does not exist!");
            }
            where = 4;
        }
        // 2. general case
        else {
            int index = 1;
            while (index < segments.length) {
                // an attribute
                if (!isPlainText(segments[index])) {
                    throw new MySQLException.InvalidQueryException("Attribute " + segments[index] + " is not a valid attribute!");
                }
                attributes.add(segments[index].toLowerCase());
                index++;
                // followed by a comma or FROM
                if (index < segments.length && ",".equals(segments[index])) {
                    index++;
                } else if (index < segments.length && "FROM".equalsIgnoreCase(segments[index])) {
                    break;
                } else {
                    throw new MySQLException.InvalidQueryException();
                }
            }
            // index points to FROM
            table = this.currentDatabase.getTable(segments[index + 1].toLowerCase() + ".tab");
            if (table == null) {
                throw new MySQLException.InvalidQueryException("The table you would like to SELECT does not exist!");
            }
            where = index + 2;
        }

        List<Integer> indices = table.getSelectedAttributeIndices(attributes);
        ConditionParser cp = null;
        if (where < segments.length) {
            if (!"WHERE".equalsIgnoreCase(segments[where])) {
                throw new MySQLException.InvalidQueryException("Missing WHERE keyword!");
            }
            cp = new ConditionParser(table, Arrays.copyOfRange(segments, where + 1, segments.length));
        }
        return table.getSelectedRows(indices, cp);
    }

    private String handleInsertCommand(String[] segments) throws MySQLException {
        if (segments.length < 6) {
            throw new MySQLException.InvalidQueryException("Invalid usage of INSERT command.");
        }
        checkCurrentDatabase();

        if (!"INTO".equalsIgnoreCase(segments[1])) {
            throw new MySQLException.InvalidQueryException("Missing INTO keyword.");
        }

        Table table = this.currentDatabase.getTable(segments[2].toLowerCase() + ".tab");
        if (table == null) {
            throw new MySQLException.InvalidQueryException("The table you would like to INSERT does not exist!");
        }

        if (!"VALUES".equalsIgnoreCase(segments[3])) {
            throw new MySQLException.InvalidQueryException("Missing VALUES keyword.");
        }

        List<String> values = this.handleParameterList(Arrays.copyOfRange(segments, 4, segments.length), 'v');
        table.appendRow(values);
        return "[OK]";
    }

    private String handleAlterCommand(String[] segments) throws MySQLException {
        if (segments.length != 5) {
            throw new MySQLException.InvalidQueryException("Invalid usage of ALTER command.");
        }
        checkCurrentDatabase();

        if (!"TABLE".equalsIgnoreCase(segments[1])) {
            throw new MySQLException.InvalidQueryException("Missing TABLE keyword.");
        }

        Table table = this.currentDatabase.getTable(segments[2].toLowerCase() + ".tab");
        if (table == null) {
            throw new MySQLException.InvalidQueryException("The table you would like to ALTER does not exist!");
        }

        if (!"ADD".equalsIgnoreCase(segments[3]) && !"DROP".equalsIgnoreCase(segments[3])) {
            throw new MySQLException.InvalidQueryException("Missing ADD or DROP keyword, or invalid type of alteration.");
        }

        if ("ADD".equalsIgnoreCase(segments[3])) {
            table.updateColumn(segments[4], Table.Action.ADD);
            return "[OK]";
        }
        if ("DROP".equalsIgnoreCase(segments[3])) {
            table.updateColumn(segments[4], Table.Action.DROP);
            return "[OK]";
        }
        throw new MySQLException("The attribute you would like to ALTER does not exist!");
    }

    private String handleDropCommand(String[] segments) throws MySQLException {
        if (segments.length != 3) {
            throw new MySQLException.InvalidQueryException("Invalid usage of DROP command.");
        }

        if ("DATABASE".equalsIgnoreCase(segments[1])) {
            return handleDropDatabase(segments);
        } else if ("TABLE".equalsIgnoreCase(segments[1])) {
            return handleDropTable(segments);
        } else {
            throw new MySQLException.InvalidQueryException("Invalid usage of DROP command.");
        }
    }

    private String handleDropDatabase(String[] segments) throws MySQLException {
        File db = new File(this.storageFolderPath, segments[2].toLowerCase());
        if (!db.exists()) {
            throw new MySQLException("The database you would like to DROP does not exist!");
        } else {
            if (db.delete()) {
                return "[OK]";
            } else {
                throw new MySQLException("Failed to DROP the database!");
            }
        }
    }

    private String handleDropTable(String[] segments) throws MySQLException {
        checkCurrentDatabase();

        File table = this.currentDatabase.getTableFile(segments[2].toLowerCase() + ".tab");
        if (table == null) {
            throw new MySQLException("The table you would like to DROP does not exist!");
        } else {
            if (table.delete()) {
                File tableID = new File(table.getParentFile(), segments[2].toLowerCase() + ".id");
                if (tableID.exists()) tableID.delete();
                this.currentDatabase.removeTable(segments[2].toLowerCase());
                return "[OK]";
            } else {
                throw new MySQLException("Failed to DROP the table!");
            }
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
            throw new MySQLException("This database already exists, failed to CREATE!");
        } else {
            if (db.mkdir()) {
                return "[OK]";
            } else {
                throw new MySQLException("Failed to CREATE database.");
            }
        }
    }

    private String handleCreateTable(String[] segments) throws MySQLException {
        checkCurrentDatabase();

        File table = this.currentDatabase.getTableFile(segments[2].toLowerCase());
        if (table != null) {
            throw new MySQLException("This table already exists, failed to CREATE!");
        }

        if (segments.length == 3) {
            this.currentDatabase.createNewTable(segments[2].toLowerCase());
        } else {
            List<String> attributes = this.handleParameterList(Arrays.copyOfRange(segments, 3, segments.length), 'a');
            this.currentDatabase.createNewTable(segments[2].toLowerCase(), attributes.toArray(String[]::new));
        }
        return "[OK]";
    }

    // input segments is of the format (VALUE1, VALUE2, ...)
    private List<String> handleParameterList(String[] segments, char mode) throws MySQLException {
        String type = getParameterListType(segments, mode);
        ArrayList<String> parameters = new ArrayList<>();

        // the loop begins at 1 and ends at len - 1 to skip parenthesis
        int index = 1;
        while (index < segments.length - 1) {
            // a valid parameter
            if ((mode == 'a' && !Controller.isPlainText(segments[index])) || (mode == 'v' && !Controller.isValidValue(segments[index]))) {
                throw new MySQLException.InvalidQueryException("Invalid " + type + " naming: " + segments[index]);
            }
            parameters.add(segments[index]);
            // followed by a comma
            if (",".equals(segments[index + 1])) {
                index += 2;
            } else if (index + 1 >= segments.length - 1) {
                break;
            } else {
                throw new MySQLException.InvalidQueryException("Missing comma in the " + type + " list!");
            }
        }
        return parameters;
    }

    private static String getParameterListType(String[] segments, char mode) {
        String type;
        if (mode == 'v') {
            type = "value";
        } else if (mode == 'a') {
            type = "attribute";
        } else {
            throw new MySQLException("Interior Error: unknown type of parameter list!");
        }

        // check beginning and concluding parenthesis.
        if (!"(".equals(segments[0])) {
            throw new MySQLException.InvalidQueryException("Missing left parenthesis for the " + type + " list!");
        }
        if (!")".equals(segments[segments.length - 1])) {
            throw new MySQLException.InvalidQueryException("Missing right parenthesis for the " + type + " list!");
        }
        return type;
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

    private static boolean isValidStringLiteral(String str) {
        if (!str.startsWith("'") || !str.endsWith("'")) {
            return false;
        }
        for (int i = 1; i < str.length() - 1; i++) {
            if (!Character.isLetterOrDigit(str.charAt(i)) && !Lexer.isValidSymbol(str.charAt(i)) && str.charAt(i) != ' ') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidValue(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return "TRUE".equalsIgnoreCase(str) || "FALSE".equalsIgnoreCase(str) || "NULL".equalsIgnoreCase(str) || Controller.isValidStringLiteral(str);
        }
    }

    public static boolean isPlainText(String str) {
        if (str.isEmpty()) {
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

    private void checkCurrentDatabase() throws MySQLException {
        if (this.currentDatabase == null) {
            throw new MySQLException("You have not selected a database yet!");
        }
    }
}
