package se.liaprojekt.exception;

import lombok.Getter;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus
@Getter
public class BlobOperationException extends RuntimeException {

    private final int statusCode;
    private final String azureErrorCode;

    public BlobOperationException(String message, int statusCode, String azureErrorCode) {
        super(message);
        this.statusCode = statusCode;
        this.azureErrorCode = azureErrorCode;
    }
}