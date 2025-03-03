package edu.uob;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Table {
    private File table;

    private List<List<String>> selectedTableContents; // does not load content headers

    public Table(String pathname) throws MySQLException.TableNotFoundException {
        this.table = new File(pathname);
        if (!this.table.exists()) {
            throw new MySQLException.TableNotFoundException();
        }
    }

    public Table(String parent, String child) {
        this.table = new File(parent, child);
        if (!this.table.exists()) {
            throw new MySQLException.TableNotFoundException();
        }
    }

    public Table(File parent, String child) {
        this.table = new File(parent, child);
        if (!this.table.exists()) {
            throw new MySQLException.TableNotFoundException();
        }
    }

    public File getTableFile() {
        return this.table;
    }

    public String getNameWithoutExtension() {
        String name = this.table.getName();
        return name.substring(0, name.lastIndexOf('.')); // remove file extension
    }

    // case-insensitive
    // store attributes along with their indices in a hash map
    public HashMap<String, Integer> getAllAttributes() throws MySQLException {
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(this.table))) {
            line = br.readLine();
        } catch (IOException e) {
            throw new MySQLException.MyIOException(e.getMessage());
        }
        if (line == null) {
            return null;
        }
        String[] arr = line.split("\t");
        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < arr.length; i++) {
            map.put(arr[i].toLowerCase(), i);
        }
        return map;
    }

    public List<String> getAllAttributesList() throws MySQLException {
        return getAllAttributes().entrySet().stream()
                .sorted((o1, o2) -> o1.getValue() - o2.getValue())
                .map(e -> e.getKey())
                .toList();
    }

    public int getCountAttributes() throws MySQLException {
        return getAllAttributes().size();
    }

    public int getAttributeIndex(String name) throws MySQLException {
        Integer index = getAllAttributes().get(name.toLowerCase());
        return index != null ? index : -1;
    }

    // notice that, the selected attributes might be duplicate.
    public List<Integer> getSelectedAttributeIndices(List<String> attributes) throws MySQLException {
        Map<String, Integer> map = getAllAttributes();
        ArrayList<Integer> indices = new ArrayList<>();

        // wildcard
        if (attributes.size() == 1 && "*".equals(attributes.get(0))) {
            for (int i = 0; i < getCountAttributes(); i++) {
                indices.add(i);
            }
        }

        // general case
        else {
            for (String attribute : attributes) {
                if (!map.containsKey(attribute)) {
                    throw new MySQLException.InvalidQueryException("Attribute " + attribute + " that your would like to query does not exist!");
                }
                indices.add(map.get(attribute));
            }
        }

        return indices;
    }

    private long getMaxID() throws IOException, MySQLException {
        BufferedReader br = new BufferedReader(new FileReader(this.table));
        long maxID = 0L;
        String line = br.readLine(); // skip the first header line
        while ((line = br.readLine()) != null) {
            if (line.isEmpty()) continue;
            try {
                long tmp = Long.parseLong(line.split("\t")[0]);
                maxID = Math.max(tmp, maxID);
            } catch (NumberFormatException e) {
                throw new MySQLException.FileCrackedException("The ID of the record of this table has been cracked.");
            }
        }
        br.close();
        return maxID;
    }

    private String getUniqueID() throws IOException {
        File id = new File(this.table.getParentFile(), getNameWithoutExtension() + ".id");

        // if ID file does not exist, create one.
        if (!id.exists() && !id.createNewFile()) {
            throw new IOException("Failed to create ID file.");
        }

        // read the first line of the ID file, if it is not a number, read the table to update the ID file.
        Long maxID;
        BufferedReader br = new BufferedReader(new FileReader(id));
        String line = br.readLine();
        br.close();
        try {
            maxID = Long.parseLong(line) + 1;
        } catch (NumberFormatException e) {
            maxID = getMaxID() + 1;
        }

        // write the maxID into ID file.
        BufferedWriter bw = new BufferedWriter(new FileWriter(id, false));
        bw.write(String.valueOf(maxID));
        bw.newLine();
        bw.flush();
        bw.close();

        return String.valueOf(maxID);
    }

    // load selected table contents: columns according to indices, rows according to cp.
    public void loadTableContents(List<Integer> indices, ConditionParser cp) throws MySQLException {
        this.selectedTableContents = new LinkedList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(this.table))) {
            String line = br.readLine(); // skip headers

            // consider condition for data
            while ((line = br.readLine()) != null) {
                List<String> row = new LinkedList<>();
                String[] arr = line.split("\t");
                if (cp != null && !cp.getResult(arr)) continue;
                for (Integer index : indices) {
                    row.add(arr[index]);
                }
                this.selectedTableContents.add(row);
            }

        } catch (IOException e) {
            throw new MySQLException.MyIOException(e.getMessage());
        }
    }

    public List<List<String>> getTableContents() {
        return this.selectedTableContents;
    }

    public enum ColumnAction {ADD, DROP}

    public void updateColumn(String name, ColumnAction action) throws MySQLException {
        String actionType = action == ColumnAction.ADD ? "add new" : "drop";
        if (action == ColumnAction.DROP && "id".equalsIgnoreCase(name)) {
            throw new MySQLException.InvalidQueryException("You cannot manually drop ID attribute!");
        }

        File tmpFile = new File("tmp$" + this.table.getName());

        try (BufferedReader br = new BufferedReader(new FileReader(this.table));
             BufferedWriter bw = new BufferedWriter(new FileWriter(tmpFile))) {
            if (action == ColumnAction.ADD && getAttributeIndex(name) != -1) {
                throw new MySQLException.InvalidQueryException("You cannot add duplicate attribute!");
            }

            if (action == ColumnAction.DROP) {
                addColumn(name, br, bw);
            } else {
                dropColumn(name, br, bw);
            }
            bw.flush();
            bw.close();
            br.close();

            // delete old file, rename tmp file.
            this.table = getNewTableFile("Failed to delete the original table file during " + actionType + " attribute.",
                    tmpFile, " during " + actionType + " attribute.");

        } catch (IOException e) {
            throw new MySQLException.MyIOException("IOException: Failed to " + actionType + " attribute.");
        }
    }

    private void addColumn(String name, BufferedReader br, BufferedWriter bw) throws IOException {
        // if the attribute to drop does not exist or the file is empty, throw exception.
        int index = getAttributeIndex(name);
        int countAttributes = getCountAttributes();
        if (index == -1 || countAttributes == -1) {
            throw new MySQLException.NoSuchAttributeException("The attribute you would like to drop does not exist.");
        }
        // transfer old file into a tmp file.
        String line;
        while ((line = br.readLine()) != null) {
            String[] tmp = line.split("\t");
            if (tmp.length != countAttributes) {
                throw new MySQLException.FileCrackedException();
            }
            List<String> list = new LinkedList<>(List.of(tmp));
            list.remove(index);
            bw.write(String.join("\t", list));
            bw.newLine();
        }
    }

    private void dropColumn(String name, BufferedReader br, BufferedWriter bw) throws IOException {
        // transfer old file into a tmp file.
        String line = br.readLine();
        bw.write(line + "\t" + name + System.lineSeparator());
        while ((line = br.readLine()) != null) {
            bw.write(line + "\tNULL");
            bw.newLine();
        }
    }

    public void appendRow(List<String> values) throws MySQLException {
        // checks if the number of input values match the number of attributes.
        if (values.size() != getCountAttributes() - 1) {
            throw new MySQLException.InvalidQueryException("The number of input values does not match the table.");
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(this.table, true))) {
            bw.write(getUniqueID() + "\t" + Utility.removeStringQuotes(String.join("\t", values)));
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            throw new MySQLException.MyIOException(e.getMessage());
        }
    }

    public String getSelectedRows(List<Integer> indices, ConditionParser cp) throws MySQLException {
        loadTableContents(indices, cp);

        StringBuffer sb = new StringBuffer();
        sb.append("[OK]").append(System.lineSeparator());

        List<List<String>> contents = new ArrayList<>();
        contents.add(getAllAttributesList());
        contents.addAll(this.selectedTableContents);

        return Utility.formatMatrix(contents);
    }

    // if NameValueMap == null, this will delete rows.
    public void updateSelectedRows(Map<Integer, String> NameValueMap, ConditionParser cp) throws MySQLException {
        File tmpFile = new File("tmp$" + this.table.getName());
        try (BufferedReader br = new BufferedReader(new FileReader(this.table));
             BufferedWriter bw = new BufferedWriter(new FileWriter(tmpFile))) {
            String line = br.readLine(); // skip the headers
            bw.write(line + System.lineSeparator()); // but remember to keep the headers

            while ((line = br.readLine()) != null) {
                String[] arr = line.split("\t");
                if (cp != null && cp.getResult(arr)) {
                    // update this row if NameValueMap is passed, otherwise delete it
                    if (NameValueMap != null) {
                        for (int i = 0; i < arr.length; i++) {
                            if (NameValueMap.containsKey(i)) {
                                arr[i] = NameValueMap.get(i);
                            }
                        }
                    } else {
                        continue;
                    }
                }
                bw.write(String.join("\t", arr) + System.lineSeparator());
            }

            bw.flush();
            bw.close();
            br.close();

            // delete old file, rename tmp file.
            this.table = getNewTableFile("Failed to delete the original table file during UPDATE.", tmpFile, " during UPDATE.");

        } catch (IOException e) {
            throw new MySQLException.MyIOException(e.getMessage());
        }
    }

    public static String joinTables(Table table1, Table table2, String[] segments) throws MySQLException {
        int index1 = table1.getAttributeIndex(segments[5]);
        int index2 = table2.getAttributeIndex(segments[7]);
        if (index1 == -1 || index2 == -1) {
            throw new MySQLException.InvalidQueryException("The attributes you would like to match do not exist!");
        }

        List<List<String>> jointTable = new ArrayList<>();

        // insert formatted headers
        formatJointTableHeaders(table1, table2, index1, index2, jointTable);

        // get joint table contents
        Map<Integer, List<String>> unsortedResult = jointTableContents(table1, table2, index1, index2);

        // convert map into sorted list
        List<List<String>> sortedResult = unsortedResult.entrySet().stream()
                .sorted((o1, o2) -> o1.getKey() - o2.getKey())
                .map(entry -> entry.getValue())
                .toList();

        // loop through the sorted list to generate id
        int id = 1;
        for (List<String> row : sortedResult) {
            row.add(0, String.valueOf(id));
            id++;
        }

        jointTable.addAll(sortedResult);
        return Utility.formatMatrix(jointTable);
    }

    private static Map<Integer, List<String>> jointTableContents(Table table1, Table table2, int index1, int index2) throws MySQLException {
        // load and get all table contents
        table1.loadTableContents(table1.getSelectedAttributeIndices(List.of("*")), null);
        table2.loadTableContents(table2.getSelectedAttributeIndices(List.of("*")), null);
        List<List<String>> contents1 = table1.getTableContents();
        List<List<String>> contents2 = table2.getTableContents();

        // key: the values of the column to be matched from table1, value: the corresponding row
        Map<String, List<String>> contents1Map = contents1.stream().collect(Collectors.toMap(s -> s.get(index1), s -> s));

        if(contents1Map.size() != contents1.size()){
            throw new MySQLException.FileCrackedException("The attribute you would like to match has duplicate values and cannot be used as a key!");
        }

        // use another hash map, so we can sort the result to match table1's order.
        Map<Integer, List<String>> unsortedResult = new HashMap<>();

        // now loop through contents2 to match
        for (List<String> row : contents2) {
            if (contents1Map.containsKey(row.get(index2))) {
                // format the joint row
                List<String> newRow = new LinkedList<>();

                // values from table1
                List<String> values1 = contents1Map.get(row.get(index2));
                if(unsortedResult.containsKey(contents1.indexOf(values1))){
                    throw new MySQLException.FileCrackedException("The attribute you would like to match has duplicate values and cannot be used as a key!");
                }
                for (int i = 0; i < values1.size(); i++) {
                    if (i == 0 || i == index1) continue;
                    newRow.add((values1.get(i)));
                }

                // values from table2
                for (int i = 0; i < row.size(); i++) {
                    if (i == 0 || i == index2) continue;
                    newRow.add(row.get(i));
                }
                unsortedResult.put(contents1.indexOf(values1), newRow);
            }
        }
        return unsortedResult;
    }

    private static void formatJointTableHeaders(Table table1, Table table2, int index1, int index2, List<List<String>> jointTable) {
        List<String> headers = new LinkedList<>();
        headers.add("id");
        List<String> headers1 = table1.getAllAttributesList();
        for (int i = 0; i < headers1.size(); i++) {
            if (i == 0 || i == index1) continue;
            headers.add(table1.getNameWithoutExtension() + "." + headers1.get(i));
        }
        List<String> headers2 = table2.getAllAttributesList();
        for (int i = 0; i < headers2.size(); i++) {
            if (i == 0 || i == index2) continue;
            headers.add(table2.getNameWithoutExtension() + "." + headers2.get(i));
        }
        jointTable.add(headers);
    }

    // replace current table with tmp table, and rename it
    private File getNewTableFile(String message, File tmpFile, String phase) {
        String tableName = this.table.getName();
        File newTableFile = new File(this.table.getParentFile(), tableName);
        if (!this.table.delete()) {
            throw new MySQLException.MyIOException(message);
        }
        if (!tmpFile.renameTo(newTableFile)) {
            throw new MySQLException.MyIOException("Failed to rename temp file to " + tableName + phase);
        }
        return newTableFile;
    }


}
