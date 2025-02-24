package edu.uob;

import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

public class Lexer {
    private enum Keywords {
        USE, CREATE, DROP, ALTER, INSERT, SELECT, UPDATE, DELETE, JOIN
    }

    private static Set<String> typeKeywords = new HashSet<>();

    static {
        Lexer.typeKeywords.add("USE");
        Lexer.typeKeywords.add("CREATE");
        Lexer.typeKeywords.add("DROP");
        Lexer.typeKeywords.add("ALTER");
        Lexer.typeKeywords.add("INSERT");
        Lexer.typeKeywords.add("SELECT");
        Lexer.typeKeywords.add("UPDATE");
        Lexer.typeKeywords.add("DELETE");
        Lexer.typeKeywords.add("JOIN");
    }

    private Lexer() {
    }

    public static boolean isValidCommand(String[] segments) {
        return Lexer.typeKeywords.contains(segments[0].toUpperCase());
    }

    public static String[] lexTokens(String command) {
        List<String> segments = new ArrayList<>();
        command = command.trim();
        while (command.length() > 0) {
            if (command.charAt(0) == '\'') {
                String[] tmp = Lexer.handleStringLiteral(command);
                segments.add(tmp[0]);
                if (tmp.length == 1) {
                    break;
                } else {
                    command = tmp[1];
                }
            }
        }
        return segments.toArray(String[]::new);
    }

    // return 1~2 strings [word, ?restCommand]
    private static String[] handleWholeWord(String _command) {
        // remove white spaces
        String command = _command.trim();

        int index = command.length();
        for (int i = 0; i < command.length(); i++) {
            if (command.charAt(i) == ' ') {
                index = i;
            }
        }
        if (index == command.length() - 1) {
            return new String[]{command.substring(0, index + 1), command.substring(index + 1)};
        } else {
            return new String[]{command};
        }
    }

    // return 1~2 strings [stringLiteral, ?restCommand]
    private static String[] handleStringLiteral(String command) {
        // command must start with a '
        if (!command.startsWith("'")) {
            throw new RuntimeException("command segment does not begin with a ': " + command);
        }
        int index = -1;
        for (int i = 1; i < command.length(); i++) {
            if (command.charAt(i) == '\'') {
                index = i;
                break;
            }
        }
        if (index == -1) {
            throw new RuntimeException("command segment does not have a concluding ': " + command);
        }
        if (index == command.length() - 1) {
            return new String[]{command.substring(0, index + 1), command.substring(index + 1)};
        } else {
            return new String[]{command};
        }
    }
}
