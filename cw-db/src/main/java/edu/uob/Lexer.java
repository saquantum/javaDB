package edu.uob;

import java.util.*;

public class Lexer {
    private static Set<Character> validChars = new HashSet<>();
    static {
        Collections.addAll(Lexer.validChars, '!', '#', '$', '%', '&', '(', ')', '*', '+', ',', '-', '.', '/', ':', ';', '>', '=', '<', '?', '@', '[', '\\', ']', '^', '_', '`', '{', '}', '~');
    }

    public static String[] lexTokens(String command) throws MySQLException {
        List<String> segments = new LinkedList<>();
        command = command.trim();
        while (!command.isEmpty()) {
            String[] tmp;
            if (command.charAt(0) == '\'') {
                tmp = Lexer.handleStringLiteral(command);
            }
            else if (Character.isDigit(command.charAt(0))) {
                tmp = Lexer.handleNumberLiteral(command);
                try {
                    Double.parseDouble(tmp[0]);
                } catch (NumberFormatException e) {
                    // if not a number, we try again to lex it as a word
                    tmp = Lexer.handleWholeWord(command);
                    //throw new MySQLException.InvalidQueryException("Invalid number format.");
                }
            }
            else if (Character.isLetterOrDigit(command.charAt(0))) {
                tmp = Lexer.handleWholeWord(command);
            } else if (Lexer.isValidSymbol(command.charAt(0))) {
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

            // finished handling a token, add it to the token table
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
        // 1. lex it as the union of number and plain text.
        // valid chars: letters, numbers, dot, underscore.
        for (int i = 0; i < command.length(); i++) {
            if (!Character.isLetterOrDigit(command.charAt(i)) && command.charAt(i) != '_' && command.charAt(i) != '.') {
                index = i;
                break;
            }
        }
        // 2. check if this is a valid number:
        String tmp = command.substring(0, index);
        try{
            Double.parseDouble(tmp);
        }catch(NumberFormatException e){
            // 3. not a valid number, try to check if it is a valid plain text:
            // it cannot contain dot.
            for (int i = 0; i < tmp.length(); i++) {
                // not a number and not plain text, wrong.
                if(tmp.charAt(i) == '.'){
                    throw new MySQLException.InvalidQueryException();
                }
            }
        }

        if (index < command.length()) {
            return new String[]{tmp, command.substring(index)};
        } else {
            return new String[]{tmp};
        }
    }

    public static boolean isValidSymbol(char c) {
        return Lexer.validChars.contains(c);
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
