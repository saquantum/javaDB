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
        try {
            String[] segments = Lexer.lexTokens(command);

            if (!Controller.isValidCommand(segments)) {
                return "[ERROR]: Unknown type of command.";
            }
            if (!";".equals(segments[segments.length - 1])) {
                return "[ERROR]: A SQL statement must end with a semicolon!";
            }
            segments = Arrays.copyOf(segments, segments.length - 1);
            Keywords key = Controller.typeKeywords.get(segments[0].toUpperCase());

            if (key == Keywords.USE) {
                return this.handleUseCommand(segments);
            } else if (key == Keywords.CREATE) {
                return this.handleCreateCommand(segments);
            } else if (key == Keywords.DROP) {
                return this.handleDropCommand(segments);
            } else if (key == Keywords.ALTER) {
                return this.handleAlterCommand(segments);
            } else if (key == Keywords.INSERT) {
                return this.handleInsertCommand(segments);
            } else if (key == Keywords.SELECT) {
                return this.handleSelectCommand(segments);
            } else if (key == Keywords.UPDATE) {
                return this.handleUpdateCommand(segments);
            } else if (key == Keywords.DELETE) {
                return this.handleDeleteCommand(segments);
            } else if (key == Keywords.JOIN) {
                return this.handleJoinCommand(segments);
            } else {
                return "[ERROR]: Invalid type of command."; // this should never be reached however
            }
        } catch (Exception e) {
            return "[ERROR]: " + e.getMessage();
        }
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

        return "[OK]" + System.lineSeparator() + Table.joinTables(table1, table2, segments);
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

        return parseUpdateCommand(table, segments);
    }

    private String parseUpdateCommand(Table table, String[] segments) throws MySQLException {
        Map<Integer, String> map = new HashMap<>(); // <Integer: index of attribute, String: new value>

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
            map.put(key, Utility.removeStringQuotes(segments[index]));
            index++;
            // followed by a WHERE or a comma
            if (index < segments.length && "WHERE".equalsIgnoreCase(segments[index])) {
                break;
            } else if(index < segments.length && ",".equalsIgnoreCase(segments[index]) ){
                index++;
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

        // 1. wildcard case.
        if ("*".equals(segments[1]) && "FROM".equalsIgnoreCase(segments[2])) {
            return "[OK]" + System.lineSeparator() + handleSelectWildcard(segments);
        }
        // 2. general case
        else {
            return "[OK]" + System.lineSeparator() + handleSelectGeneral(segments);
        }
    }

    private String handleSelectGeneral(String[] segments) throws MySQLException {
        List<String> attributes = new ArrayList<>();

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
        Table table = this.currentDatabase.getTable(segments[index + 1].toLowerCase() + ".tab");
        if (table == null) {
            throw new MySQLException.InvalidQueryException("The table you would like to SELECT does not exist!");
        }

        return getSelectResult(table, attributes, segments, index + 2);
    }

    private String handleSelectWildcard(String[] segments) throws MySQLException {

        List<String> attributes = List.of("*");

        Table table = this.currentDatabase.getTable(segments[3].toLowerCase() + ".tab");
        if (table == null) {
            throw new MySQLException.InvalidQueryException("The table you would like to SELECT does not exist!");
        }

        return getSelectResult(table, attributes, segments, 4);
    }

    private String getSelectResult(Table table, List<String> attributes, String[] segments, int where) throws MySQLException {
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
            table.updateColumn(segments[4], Table.ColumnAction.ADD);
            return "[OK]";
        }
        if ("DROP".equalsIgnoreCase(segments[3])) {
            table.updateColumn(segments[4], Table.ColumnAction.DROP);
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
            if (this.currentDatabase != null && this.currentDatabase.getDatabase().getName().equalsIgnoreCase(db.getName())) {
                this.currentDatabase = null;
            }
            if (Utility.deleteRecursive(db)) {
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
            index++;
            if (")".equals(segments[index])) {
                break;
            }
            // followed by a comma
            if (!",".equals(segments[index])) {
                throw new MySQLException.InvalidQueryException("Missing comma in the " + type + " list!");
            }
            index++;
            // only valid parameter follows comma
            if (index >= segments.length - 1 || (mode == 'a' && !Controller.isPlainText(segments[index])) || (mode == 'v' && !Controller.isValidValue(segments[index]))) {
                throw new MySQLException.InvalidQueryException("Invalid " + type + " naming: " + segments[index]);
            }
        }
        return parameters;
    }

    private static String getParameterListType(String[] segments, char mode) throws MySQLException {
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
