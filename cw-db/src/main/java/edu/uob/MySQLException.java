package edu.uob;

public class MySQLException extends RuntimeException{
    protected String message;
    public MySQLException(String message){
        super(message);
        this.message = message;
    }

    public static class InvalidQueryException extends MySQLException{
        public InvalidQueryException(){
            super("Invalid SQL language.");
        }

        public InvalidQueryException(String message){
            super(message);
        }

        public String errorMessage(){
            return this.message;
        }
    }
}
