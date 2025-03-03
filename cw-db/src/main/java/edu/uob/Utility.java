package edu.uob;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Utility {

    private Utility() {
    }

    public static boolean deleteRecursive(File dir) throws MySQLException {
        if (dir == null || !dir.exists()) {
            return true;
        }

        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files == null) {
                throw new MySQLException.MyIOException("Cannot list files in " + dir.getAbsolutePath());
            }
            for (File file : files) {
                if (!deleteRecursive(file)) return false;
            }
        }
        return dir.delete();
    }

    public static String removeStringQuotes(String str) throws MySQLException {
        try {
            return str.replaceAll("'", "");
        } catch (NullPointerException e) {
            throw new MySQLException.NullPointerException(e.getMessage());
        }
    }

    public static String formatMatrix(List<List<String>> matrix) throws MySQLException {
        if (matrix.isEmpty()) return "";

        int[] maxLength = new int[matrix.get(0).size()];

        try {
            for (List<String> list : matrix) {
                for (int i = 0; i < list.size(); i++) {
                    maxLength[i] = Math.max(maxLength[i], list.get(i).length());
                }
            }
        } catch (IndexOutOfBoundsException e) {
            throw new MySQLException.FileCrackedException(e.getMessage());
        }

        int totalLength = Arrays.stream(maxLength).sum() + maxLength.length - 1;

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < matrix.size(); i++) {
            if(i == 1) sb.append("-".repeat(totalLength)).append(System.lineSeparator());
            List<String> row = matrix.get(i);
            for (int j = 0; j < row.size(); j++) {
                sb.append(row.get(j)).append(" ".repeat(maxLength[j] + 1 - row.get(j).length()));
            }
            sb.append(System.lineSeparator());
        }




        return sb.toString();
    }
}
