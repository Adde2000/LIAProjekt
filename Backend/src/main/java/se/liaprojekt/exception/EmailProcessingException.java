package se.liaprojekt.exception;

public class EmailProcessingException extends RuntimeException {

    public EmailProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
