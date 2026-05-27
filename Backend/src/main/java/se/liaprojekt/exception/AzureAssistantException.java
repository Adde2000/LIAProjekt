package se.liaprojekt.exception;

public class AzureAssistantException extends RuntimeException {

    public AzureAssistantException(String message) {
        super(message);
    }

    public AzureAssistantException(String message, Throwable cause) {
        super(message, cause);
    }
}