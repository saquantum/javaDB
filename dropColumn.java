public enum Action {ADD, DROP}

public void updateColumn(String name, Action action) throws MySQLException {
        String actionType = action==Action.ADD? "add new" : "drop";
        if (action == Action.DROP && "id".equalsIgnoreCase(name)) {
            throw new MySQLException.InvalidQueryException("You cannot manually drop ID attribute!");
        }

        File tmpFile = new File("tmp$" + this.table.getName());
        BufferedReader br = null;
        BufferedWriter bw = null;
        try {
            
            if (action == Action.ADD && getAttributeIndex(name) != -1) {
                throw new MySQLException.InvalidQueryException("You cannot add duplicate attribute!");
            }
            
            br = new BufferedReader(new FileReader(this.table));
            bw = new BufferedWriter(new FileWriter(tmpFile));

        if(action == Action.DROP){
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
            }}else{
            // transfer old file into a tmp file.
            String line = br.readLine();
            bw.write(line + "\t" + name + System.lineSeparator());
            while ((line = br.readLine()) != null) {
                bw.write(line + "\tNULL");
                bw.newLine();
            }
            }

            bw.flush();
            bw.close();
            br.close();

            // delete old file, rename tmp file.
            this.table = getNewTableFile("Failed to delete the original table file during " + actionType + " column.", tmpFile, " during dropping column.");
            
        } catch (IOException e) {
        throw new MySQLException.MyIOException("IOException: Failed to " + actionType + " attribute.");
        } finally {
            try {
                if (br != null) br.close();
                if (bw != null) bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
