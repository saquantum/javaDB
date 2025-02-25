package edu.uob;

import java.util.Arrays;

public class test {
    public static void main(String[] args) {
        String str = "  select * from table where   name='abc' and height=1.80  and    pos>= -20  ; ";
        String[] tokens = Controller.lexTokens(str);
        System.out.println(Arrays.toString(tokens));
        System.out.println(tokens[0].equals("insert"));
        System.out.println(Controller.isValidCommand(tokens));
    }
}
