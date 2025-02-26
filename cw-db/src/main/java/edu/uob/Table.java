package edu.uob;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

public class Table {
    private File table;

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

    public int getCountAttributes() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(this.table));
        String line = br.readLine();
        br.close();
        if (line == null) {
            return -1;
        }
        return line.split("\t").length;
    }

    public int getAttributeIndex(String name) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(this.table));
        String line = br.readLine();
        br.close();
        if (line == null) {
            return -1;
        }
        String[] attributes = line.split("\t");
        for (int i = 0; i < attributes.length; i++) {
            if (attributes[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
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

    private long getMaxID() throws IOException, MySQLException {
        BufferedReader br = new BufferedReader(new FileReader(this.table));
        long maxID = 1L;
        String line = br.readLine(); // skip the first header line
        while ((line = br.readLine()) != null) {
            if(line.length() == 0) continue;
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
        int count;
        try {
            count = getCountAttributes() - 1;
        } catch (IOException e) {
            throw new MySQLException.MyIOException();
        }
        if (values.size() != count) {
            throw new MySQLException.InvalidQueryException("The number of input values does not match the table.");
        }

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(this.table, true));
            bw.write(getUniqueID() + "\t" + String.join("\t", values));
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
}
