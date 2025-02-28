package edu.uob;

import java.util.List;

public class Condition {
    private Table table;

    private String attributeName;
    private int attributeIndex;
    private String comparator;
    private String value;

    public Condition(Table table, List<String> list) throws MySQLException {
        this.table = table;
        if (list.size() != 3) {
            throw new MySQLException.InvalidConditionException("The condition must consist of 3 elements!");
        }
        this.attributeName = Utility.removeStringQuotes(list.get(0));
        this.attributeIndex = table.getAttributeIndex(this.attributeName);
        if (this.attributeIndex == -1) {
            throw new MySQLException.InvalidConditionException("The attribute '" + this.attributeName + "' does not exist!");
        }
        this.comparator = list.get(1);
        this.value = Utility.removeStringQuotes(list.get(2));
    }

    private boolean isBooleanLiteral(String str) {
        return "TRUE".equalsIgnoreCase(str) || "FALSE".equalsIgnoreCase(str) || "NULL".equalsIgnoreCase(str);
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
        } catch (IndexOutOfBoundsException e) {
            throw new MySQLException.FileCrackedException("Index out of bound: A record from current table is cracked.");
        }

        if (">".equals(this.comparator) || "<".equals(this.comparator) || ">=".equals(this.comparator) || "<=".equals(this.comparator)) {
            if (!isNumberLiteral(attributeValue) || !isNumberLiteral(this.value)) {
                return false;
            }
            if (isIntegerLiteral(attributeValue) && isIntegerLiteral(this.value)) {
                return compareInteger(attributeValue);
            }
            if (isNumberLiteral(attributeValue) && isNumberLiteral(this.value)) {
                return compareDouble(attributeValue);
            }
        }

        if ("==".equals(this.comparator) || "!=".equals(this.comparator)) {
            if (isIntegerLiteral(attributeValue) && isIntegerLiteral(this.value)) {
                return compareInteger(attributeValue);
            }
            if (isNumberLiteral(attributeValue) && isNumberLiteral(this.value)) {
                return compareDouble(attributeValue);
            }
            if(isBooleanLiteral(attributeValue) && isBooleanLiteral(this.value)) {
                return this.value.equalsIgnoreCase(attributeValue);
            }
            if ("==".equals(this.comparator)) {
                return this.value.equals(attributeValue);
            } else if ("!=".equals(this.comparator)) {
                return !this.value.equals(attributeValue);
            }
        }

        if ("LIKE".equalsIgnoreCase(this.comparator)) {
            String regex = this.value.replaceAll("%", ".*").replaceAll("_", ".");
            return attributeValue.matches(regex);
        }

        return false;
    }

    private boolean compareInteger(String attributeValue) {
        if (!isIntegerLiteral(attributeValue)) {
            //throw new MySQLException.InvalidConditionException("The data you would like to query are not integer numbers.");
            return false;
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
        return false;
    }

    private boolean compareDouble(String attributeValue) {
        if (!isNumberLiteral(attributeValue)) {
            //throw new MySQLException.InvalidConditionException("The data you would like to query are not floating point numbers.");
            return false;
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
        return false;
    }

    public String toString() {
        return this.attributeName + " " + this.comparator + " " + this.value;
    }
}
