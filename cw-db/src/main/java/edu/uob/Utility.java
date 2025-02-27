package edu.uob;

import java.util.List;

public class Utility {

    private Utility(){}

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

        StringBuffer sb = new StringBuffer();

        for (List<String> list : matrix) {
            for (int i = 0; i < list.size(); i++) {
                sb.append(list.get(i)).append(" ".repeat(maxLength[i] + 1 - list.get(i).length()));
            }
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }
}
