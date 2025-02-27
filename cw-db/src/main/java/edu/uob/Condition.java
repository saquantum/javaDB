package edu.uob;

import java.util.List;

public class Condition {
    private Table table;

    private String attributeName;
    private int attributeIndex;
    private String comparator;
    private String value;
    private Class valueType;

    class Pair {
        private String a;
        private String b;

        Pair(String a, String b) {
            this.a = a;
            this.b = b;
        }

        String either() {
            return this.a;
        }

        String other(String x) {
            if (this.a.equals(x)) {
                return this.b;
            } else {
                return this.a;
            }
        }
    }

    public Condition(Table table, List<String> list) throws MySQLException {
        this.table = table;
        if (list.size() != 3) {
            throw new MySQLException.InvalidConditionException();
        }
        String comparator = list.get(1);
        Pair pair = new Pair(Table.removeStringQuotes(list.get(0)), Table.removeStringQuotes(list.get(2)));

        // one is boolean literal, the other must be an attribute, and the comparator can only be "==" or "!=".
        if (isBooleanLiteral(pair.either()) || isBooleanLiteral(pair.other(pair.either()))) {
            if (!"==".equals(comparator) && !"!=".equals(comparator)) {
                throw new MySQLException.InvalidConditionException("Invalid comparator for a comparison between boolean values!");
            }
            this.comparator = comparator;

            String bool = isBooleanLiteral(pair.either()) ? pair.either() : pair.other(pair.either());
            String attribute = pair.other(bool);
            assignAttribute(attribute);
            this.value = bool;
            this.valueType = Boolean.class;
        }

        // one is number, the other must be an attribute, and the comparator cannot be "LIKE"
        else if (isNumberLiteral(pair.either()) || isNumberLiteral(pair.other(pair.either()))) {
            if ("LIKE".equalsIgnoreCase(comparator)) {
                throw new MySQLException.InvalidConditionException("Invalid comparator for a comparison between numbers!");
            }

            String number = isBooleanLiteral(pair.either()) ? pair.either() : pair.other(pair.either());
            String attribute = pair.other(number);

            // decide what comparator to receive.
            if (attribute.equals(Table.removeStringQuotes(list.get(0)))) {
                this.comparator = comparator;
            } else {
                if (">".equals(comparator)) {
                    this.comparator = "<";
                } else if ("<".equals(comparator)) {
                    this.comparator = ">";
                } else if ("<=".equals(comparator)) {
                    this.comparator = ">=";
                } else if (">=".equals(comparator)) {
                    this.comparator = "<=";
                }
            }

            assignAttribute(attribute);
            this.value = number;
            this.valueType = isIntegerLiteral(number) ? Integer.class : Double.class;
        }

        // both are string, comparator can only be "==", "!=" and "LIKE".
        else {
            if (!"==".equals(comparator) && !"!=".equals(comparator) && !"LIKE".equalsIgnoreCase(comparator)) {
                throw new MySQLException.InvalidConditionException("Invalid comparator for a comparison between strings!");
            }
            this.comparator = comparator;

            // this case, the first string must be the attribute, or it will severely go wrong.
            String value = Table.removeStringQuotes(list.get(2));
            String attribute = Table.removeStringQuotes(list.get(0));
            assignAttribute(attribute);
            this.value = value;
            this.valueType = String.class;
        }

    }

    // checks if the attribute is valid and exists, then record its index.
    private void assignAttribute(String attribute) throws MySQLException {
        int index = table.getAttributeIndex(attribute);
        if (!Controller.isPlainText(attribute) || index == -1) {
            throw new MySQLException.InvalidConditionException("The attribute is invalid or does not exist!");
        }
        this.attributeIndex = index;
        this.attributeName = attribute;
    }

    private boolean isBooleanLiteral(String str) {
        return "TRUE".equals(str) || "FALSE".equals(str) || "NULL".equals(str);
    }

    private boolean isNumberLiteral(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isIntegerLiteral(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // pass a row of the table and get a result.
    public boolean compare(String[] row) throws MySQLException {
        if (row.length != this.table.getCountAttributes()) {
            throw new MySQLException.FileCrackedException("A record from current table is cracked.");
        }

        String attributeValue;
        try {
            attributeValue = row[this.attributeIndex];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new MySQLException.FileCrackedException("Index out of bound: A record from current table is cracked.");
        }

        if (this.valueType == Boolean.class) {
            if (!isBooleanLiteral(attributeValue)) {
                throw new MySQLException.InvalidConditionException("The data you would like to query are not boolean values.");
            }
            return this.value.equals(attributeValue);
        }

        if (this.valueType == Integer.class) {
            if (!isIntegerLiteral(attributeValue)) {
                throw new MySQLException.InvalidConditionException("The data you would like to query are not integer numbers.");
            }
            int a = Integer.parseInt(attributeValue);
            if ("==".equals(this.comparator)) {
                return a == Integer.parseInt(this.value);
            } else if (">".equals(this.comparator)) {
                return a > Integer.parseInt(this.value);
            } else if ("<".equals(this.comparator)) {
                return a < Integer.parseInt(this.value);
            } else if (">=".equals(this.comparator)) {
                return a >= Integer.parseInt(this.value);
            } else if ("<=".equals(this.comparator)) {
                return a <= Integer.parseInt(this.value);
            } else if ("!=".equals(this.comparator)) {
                return a != Integer.parseInt(this.value);
            }
        }

        if (this.valueType == Double.class) {
            if (!isNumberLiteral(attributeValue)) {
                throw new MySQLException.InvalidConditionException("The data you would like to query are not integer numbers.");
            }
            double a = Double.parseDouble(attributeValue);
            if ("==".equals(this.comparator)) {
                return a == Double.parseDouble(this.value);
            } else if (">".equals(this.comparator)) {
                return a > Double.parseDouble(this.value);
            } else if ("<".equals(this.comparator)) {
                return a < Double.parseDouble(this.value);
            } else if (">=".equals(this.comparator)) {
                return a >= Double.parseDouble(this.value);
            } else if ("<=".equals(this.comparator)) {
                return a <= Double.parseDouble(this.value);
            } else if ("!=".equals(this.comparator)) {
                return a != Double.parseDouble(this.value);
            }
        }

        if (this.valueType == String.class) {
            if ("==".equals(this.comparator)) {
                return this.value.equals(attributeValue);
            } else if ("!=".equals(this.comparator)) {
                return !this.value.equals(attributeValue);
            } else if ("LIKE".equals(this.comparator)) {
                String regex = this.value.replaceAll("%", ".*").replaceAll("_", ".");
                return attributeValue.matches(regex);
            }
        }

        throw new MySQLException.InvalidConditionException();
    }

    public String toString() {
        return this.attributeName + this.comparator + this.value;
    }

}
