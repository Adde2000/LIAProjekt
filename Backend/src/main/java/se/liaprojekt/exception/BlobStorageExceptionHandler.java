package se.liaprojekt.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class BlobStorageExceptionHandler {

    @ExceptionHandler(BlobOperationException.class)
    public ResponseEntity<Map<String, String>> handleBlobOperationException(BlobOperationException ex) {
        String userMessage = switch (ex.getStatusCode()) {
            case 404 -> "The requested file or container was not found.";
            case 403 -> "Access denied. Check the Managed Identity has the Storage Blob Data Contributor role.";
            case 409 -> "Conflict — the resource may already exist.";
            default  -> "An unexpected storage error occurred. See logs for details.";
        };

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of(
                        "error", userMessage,
                        "azureErrorCode", ex.getAzureErrorCode()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage()));
    }
}