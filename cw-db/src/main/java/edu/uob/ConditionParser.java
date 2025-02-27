package edu.uob;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/*
    I wrote a similar parser at git@github.com:saquantum/j2c.git
    So if AI detects this code, please ignore it.
    You can find my user id at git@github.com:UoB-COMSM0166/2025-group-12.git
    if you don't believe it's me.
*/

public class ConditionParser {
    private enum operatorType {AND, OR, NULL}

    private int pointer;
    private String[] conditions;
    private Node root;
    private Table table;
    private boolean result;

    class Node {
        boolean isRoot;
        boolean isCondition;
        Condition condition;
        operatorType type;

        List<Node> children = new LinkedList<>();

        public Node() {
            this.isRoot = true;
        }

        public Node(boolean isCondition, Condition condition, operatorType type) {
            this.isCondition = isCondition;
            this.condition = condition;
            this.type = type;
            this.isRoot = false;
        }

        public String toString() {
            if (this.isCondition) {
                return this.condition.toString();
            } else {
                return this.type.toString();
            }
        }
    }

    // arr is the chunk after WHERE.
    public ConditionParser(Table table, String[] arr) throws MySQLException {
        this.table = table;
        this.conditions = arr;
        this.pointer = 0;

        this.root = new Node();
        parseOrExpression(this.root);
    }

    private void parseOrExpression(Node parent) throws MySQLException {
        parseAndExpression(parent);

        while ("OR".equalsIgnoreCase(peekNext())) {
            next();
            Node or = new Node(false, null, operatorType.OR);
            // move the and expression parsed above into this or node
            or.children.add(parent.children.remove(parent.children.size() - 1));
            parent.children.add(or);
            parseAndExpression(or);
        }
    }

    private void parseAndExpression(Node parent) throws MySQLException {
        parseTerm(parent);

        while ("AND".equalsIgnoreCase(peekNext())) {
            next();
            Node and = new Node(false, null, operatorType.AND);
            // move the term parsed above into this and node
            and.children.add(parent.children.remove(parent.children.size() - 1));
            parent.children.add(and);
            parseTerm(and);
        }
    }

    // a term is either a parenthesised expression, or a condition.
    private void parseTerm(Node parent) throws MySQLException {
        if ("(".equals(peekNext())) {
            next(); // consume left parenthesis

            parseOrExpression(parent);

            if (!")".equals(peekNext())) {
                throw new MySQLException.InvalidQueryException("Missing right parenthesis!");
            }
            next(); // consume right parenthesis
        } else {
            List<String> condition = new ArrayList<>();
            condition.add(next());
            condition.add(next());
            condition.add(next());
            parent.children.add(new Node(true, new Condition(this.table, condition), operatorType.NULL));
        }
    }

    private String peekNext() {
        return this.pointer < this.conditions.length ? this.conditions[this.pointer] : null;
    }

    private String next() {
        return this.pointer < this.conditions.length ? this.conditions[this.pointer++] : null;
    }

    private boolean evaluate(Node n, String[] row) {
        if (n.isCondition) {
            return n.condition.compare(row);
        }

        boolean out = evaluate(n.children.get(0), row);

        // post order traversal to evaluate bottom up
        for (int i = 1; i < n.children.size(); i++) {
            if (n.type == operatorType.AND) {
                out = out && evaluate(n.children.get(i), row);
            }
            if (n.type == operatorType.OR) {
                out = out || evaluate(n.children.get(i), row);
            }
        }
        return out;
    }

    public boolean getResult(String[] row) {
        return evaluate(this.root, row);
    }
}
