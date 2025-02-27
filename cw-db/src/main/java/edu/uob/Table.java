package edu.uob;

import java.io.*;
import java.util.*;
import java.util.function.Function;
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

    public String getNameWithoutExtension(){
        String name = this.table.getName();
        return name.substring(0, name.lastIndexOf('.')); // remove file extension
    }

    // ignore case
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
        return getAllAttributes().entrySet().stream().sorted((o1, o2) -> o1.getValue() - o2.getValue()).map(e -> e.getKey()).toList();
    }

    public int getCountAttributes() throws MySQLException {
        return getAllAttributes().size();
    }

    public int getAttributeIndex(String name) throws MySQLException {
        Integer index = getAllAttributes().get(name.toLowerCase());
        return index != null ? index : -1;
    }

    // notice that, the selected attributes might be duplicate.
    public List<Integer> getSelectedAttributes(List<String> attributes) throws MySQLException {
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

    public void dropColumn(String name) throws MySQLException {
        if ("id".equalsIgnoreCase(name)) {
            throw new MySQLException.InvalidQueryException("You cannot manually drop ID attribute!");
        }

        File tmpFile = new File("tmp$" + this.table.getName());
        BufferedReader br = null;
        BufferedWriter bw = null;
        try {
            br = new BufferedReader(new FileReader(this.table));
            bw = new BufferedWriter(new FileWriter(tmpFile));

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

            bw.flush();
            bw.close();
            br.close();

            // delete old file, rename tmp file.
            String tableName = this.table.getName();
            File newTableFile = new File(this.table.getParentFile(), tableName);
            if (!this.table.delete()) {
                throw new MySQLException.MyIOException("Failed to delete the original table file during dropping column.");
            }
            if (!tmpFile.renameTo(newTableFile)) {
                throw new MySQLException.MyIOException("Failed to rename temp file to " + tableName + " during dropping column.");
            }
            this.table = newTableFile;
        } catch (IOException e) {
            throw new MySQLException.MyIOException("IOException: Failed to drop the attribute.");
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addNewColumn(String name) throws MySQLException {
        File tmpFile = new File("tmp$" + this.table.getName());
        BufferedReader br = null;
        BufferedWriter bw = null;
        try {
            if (getAttributeIndex(name) != -1) {
                throw new MySQLException.InvalidQueryException("You cannot add duplicate attribute!");
            }

            br = new BufferedReader(new FileReader(this.table));
            bw = new BufferedWriter(new FileWriter(tmpFile));

            // transfer old file into a tmp file.
            String line = br.readLine();
            bw.write(line + "\t" + name + System.lineSeparator());
            while ((line = br.readLine()) != null) {
                bw.write(line + "\tNULL");
                bw.newLine();
            }

            bw.flush();
            bw.close();
            br.close();

            // delete old file, rename tmp file.
            String tableName = this.table.getName();
            File newTableFile = new File(this.table.getParentFile(), tableName);
            if (!this.table.delete()) {
                throw new MySQLException.MyIOException("Failed to delete the original table file during adding column.");
            }
            if (!tmpFile.renameTo(newTableFile)) {
                throw new MySQLException.MyIOException("Failed to rename temp file to " + tableName + " during adding column.");
            }
            this.table = newTableFile;
        } catch (IOException e) {
            throw new MySQLException.MyIOException("IOException: Failed to add the attribute.");
        } finally {
            try {
                if (br != null) br.close();
                if (bw != null) bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private long getMaxID() throws IOException, MySQLException {
        BufferedReader br = new BufferedReader(new FileReader(this.table));
        long maxID = 0L;
        String line = br.readLine(); // skip the first header line
        while ((line = br.readLine()) != null) {
            if (line.length() == 0) continue;
            try {
                long tmp = Long.parseLong(line.split("\t")[0]);
                maxID = Math.max(tmp, maxID);
            } catch (NumberFormatException e) {
                throw new MySQLException.FileCrackedException("The ID of the record of this table has been cracked.");
            }
        }
        return maxID;
    }

    private String getUniqueID() throws IOException {
        File id = new File(this.table.getParentFile(), this.table.getName().replaceAll("\\.tab", ".id"));

        // if ID file does not exist, create one.
        if (!id.exists()) {
            if (!id.createNewFile()) {
                throw new IOException("Failed to create ID file.");
            }
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

    public void appendRow(List<String> values) throws MySQLException {
        // checks if the number of input values match the number of attributes.
        if (values.size() != getCountAttributes() - 1) {
            throw new MySQLException.InvalidQueryException("The number of input values does not match the table.");
        }

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(this.table, true));
            bw.write(getUniqueID() + "\t" + Table.removeStringQuotes(String.join("\t", values)));
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            throw new MySQLException.MyIOException(e.getMessage());
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String removeStringQuotes(String str) throws MySQLException {
        try {
            return str.replaceAll("'", "");
        } catch (NullPointerException e) {
            throw new MySQLException.NullPointerException(e.getMessage());
        }
    }

    public void loadTableContents(List<Integer> indices, ConditionParser cp) throws MySQLException {
        this.selectedTableContents = new LinkedList<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(this.table));

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
        } finally {
            try {
                if (br != null) br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<List<String>> getTableContents(){
        return this.selectedTableContents;
    }

    public String getSelectedRows(List<Integer> indices, ConditionParser cp) throws MySQLException {
        loadTableContents(indices, cp);

        StringBuffer sb = new StringBuffer();
        sb.append("[OK]").append(System.lineSeparator());

        // headers
        List<String> headers = getAllAttributesList();
        for (Integer index : indices) {
            sb.append(headers.get(index)).append(" ");
        }
        sb.append(System.lineSeparator());

        // contents
        for (List<String> row : this.selectedTableContents) {
            for (Integer index : indices) {
                sb.append(row.get(index)).append(" ");
            }
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    public void updateSelectedRows(Map<Integer, String> NameValueMap, ConditionParser cp) throws MySQLException {
        File tmpFile = new File("tmp$" + this.table.getName());
        BufferedReader br = null;
        BufferedWriter bw = null;
        try {
            br = new BufferedReader(new FileReader(this.table));
            bw = new BufferedWriter(new FileWriter(tmpFile));
            String line = br.readLine(); // skip the headers
            bw.write(line + System.lineSeparator()); // but remember to keep the headers

            while ((line = br.readLine()) != null) {
                String[] arr = line.split("\t");
                if (cp != null && cp.getResult(arr)) {
                    for (int i = 0; i < arr.length; i++) {
                        if (NameValueMap.containsKey(i)) {
                            arr[i] = NameValueMap.get(i);
                        }
                    }
                }
                bw.write(String.join("\t", arr) + System.lineSeparator());
            }

            bw.flush();
            bw.close();
            br.close();

            // delete old file, rename tmp file.
            String tableName = this.table.getName();
            File newTableFile = new File(this.table.getParentFile(), tableName);
            if (!this.table.delete()) {
                throw new MySQLException.MyIOException("Failed to delete the original table file during UPDATE.");
            }
            if (!tmpFile.renameTo(newTableFile)) {
                throw new MySQLException.MyIOException("Failed to rename temp file to " + tableName + " during UPDATE.");
            }
            this.table = newTableFile;

        } catch (IOException e) {
            throw new MySQLException.MyIOException(e.getMessage());
        } finally {
            try {
                if (br != null) br.close();
                if (bw != null) bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void deleteSelectedRow(ConditionParser cp) throws MySQLException {
        File tmpFile = new File("tmp$" + this.table.getName());
        BufferedReader br = null;
        BufferedWriter bw = null;
        try {
            br = new BufferedReader(new FileReader(this.table));
            bw = new BufferedWriter(new FileWriter(tmpFile));
            String line = br.readLine(); // skip the headers
            bw.write(line + System.lineSeparator()); // but remember to keep the headers

            while ((line = br.readLine()) != null) {
                String[] arr = line.split("\t");
                if (cp != null && cp.getResult(arr)) continue;
                bw.write(line + System.lineSeparator());
            }

            bw.flush();
            bw.close();
            br.close();

            // delete old file, rename tmp file.
            String tableName = this.table.getName();
            File newTableFile = new File(this.table.getParentFile(), tableName);
            if (!this.table.delete()) {
                throw new MySQLException.MyIOException("Failed to delete the original table file during DELETE.");
            }
            if (!tmpFile.renameTo(newTableFile)) {
                throw new MySQLException.MyIOException("Failed to rename temp file to " + tableName + " during DELETE.");
            }
            this.table = newTableFile;

        } catch (IOException e) {
            throw new MySQLException.MyIOException(e.getMessage());
        } finally {
            try {
                if (br != null) br.close();
                if (bw != null) bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String joinTables(Table table1, Table table2, String[] segments) throws MySQLException{
        int index1 = table1.getAttributeIndex(segments[5]);
        int index2 = table2.getAttributeIndex(segments[7]);
        if (index1 == -1 || index2 == -1) {
            throw new MySQLException.InvalidQueryException("The attributes you would like to match do not exist!");
        }

        StringBuffer sb  =new StringBuffer();
        sb.append("[OK]").append(System.lineSeparator());

        // joined headers
        sb.append("id").append(" ");
        List<String> headers1 = table1.getAllAttributesList();
        for (int i = 0; i < headers1.size(); i++) {
            if(i == 0 || i == index1) continue;
            sb.append(table1.getNameWithoutExtension())
                    .append(".").append(headers1.get(i)).append(" ");
        }

        List<String> headers2 = table2.getAllAttributesList();
        for (int i = 0; i < headers2.size(); i++) {
            if(i == 0 || i == index2) continue;
            sb.append(table2.getNameWithoutExtension())
                    .append(".").append(headers2.get(i)).append(" ");
        }
        sb.append(System.lineSeparator());

        // load table contents into memory
        List<String> wildcard = new LinkedList<>();
        wildcard.add("*");
        table1.loadTableContents(table1.getSelectedAttributes(wildcard), null);
        table2.loadTableContents(table2.getSelectedAttributes(wildcard), null);

        List<List<String>> contents1 = table1.getTableContents();
        List<List<String>> contents2 = table2.getTableContents();

        // key: the values of the column to be matched from table1, value: the corresponding row
        Map<String, List<String>> contentsMap = contents1.stream().collect(Collectors.toMap(s->s.get(index1), s->s));

        // use another hash map, so we can sort the result to match table1's order.
        Map<Integer, List<String>> unsortedResult = new HashMap<>();

        // now loop through contents2 to match
        for (List<String> row : contents2) {
            if(contentsMap.containsKey(row.get(index2))){
                List<String> newRow = new LinkedList<>();

                // values from table1
                List<String> values1 = contentsMap.get(row.get(index2));
                for (int i = 0; i < values1.size(); i++) {
                    if(i == 0 || i == index1) continue;
                    newRow.add((values1.get(i)));
                }

                // values from table2
                for (int i = 0; i < row.size(); i++) {
                    if(i == 0 || i == index2) continue;
                    newRow.add(row.get(i));
                }
                unsortedResult.put(contents1.indexOf(values1), newRow);
            }
        }

        // convert map into sorted list
        List<List<String>> sortedResult = unsortedResult.entrySet().stream().sorted((o1, o2)->o1.getKey()-o2.getKey()).
                map(entry->entry.getValue()).toList();

        // loop through the sorted list to build the final string
        int id = 1;
        for (List<String> row : sortedResult) {
            sb.append(id).append(" ");
            for (String s : row) {
                sb.append(s).append(" ");
            }
            sb.append(System.lineSeparator());
            id++;
        }

        return sb.toString();
    }
}
