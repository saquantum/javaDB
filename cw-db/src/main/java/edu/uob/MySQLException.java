package edu.uob;

public class MySQLException extends RuntimeException {
    public MySQLException(String message) {
        super(message);
    }

    public static class MyIOException extends MySQLException {
        public MyIOException() {
            super("IO Exception.");
        }

        public MyIOException(String message) {
            super(message);
        }
    }

    public static class InvalidQueryException extends MySQLException {
        public InvalidQueryException() {
            super("Invalid SQL statement.");
        }

        public InvalidQueryException(String message) {
            super(message);
        }
    }

    public static class DatabaseNotFoundException extends MySQLException {
        public DatabaseNotFoundException() {
            super("Unable to find the database.");
        }

        public DatabaseNotFoundException(String message) {
            super(message);
        }
    }

    public static class TableNotFoundException extends MySQLException {
        public TableNotFoundException() {
            super("Unable to find the table.");
        }

        public TableNotFoundException(String message) {
            super(message);
        }
    }

    public static class NoSuchAttributeException extends MySQLException {
        public NoSuchAttributeException() {
            super("Unable to find the attribute.");
        }

        public NoSuchAttributeException(String message) {
            super(message);
        }
    }

    public static class FileCrackedException extends MySQLException {
        public FileCrackedException() {
            super("The table has been cracked.");
        }

        public FileCrackedException(String message) {
            super(message);
        }
    }
}
