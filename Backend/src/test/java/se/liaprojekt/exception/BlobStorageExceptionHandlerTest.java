package se.liaprojekt.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlobStorageExceptionHandlerTest {

    private BlobStorageExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BlobStorageExceptionHandler();
    }

    @Test
    void shouldHandle404BlobOperationException() {

        BlobOperationException ex =
                new BlobOperationException(
                        "Blob missing",
                        404,
                        "BlobNotFound"
                );

        ResponseEntity<Map<String, String>> response =
                handler.handleBlobOperationException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(
                "The requested file or container was not found.",
                response.getBody().get("error")
        );

        assertEquals(
                "BlobNotFound",
                response.getBody().get("azureErrorCode")
        );
    }

    @Test
    void shouldHandle403BlobOperationException() {

        BlobOperationException ex =
                new BlobOperationException(
                        "Forbidden",
                        403,
                        "AuthorizationFailure"
                );

        ResponseEntity<Map<String, String>> response =
                handler.handleBlobOperationException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(
                "Access denied. Check the Managed Identity has the Storage Blob Data Contributor role.",
                response.getBody().get("error")
        );

        assertEquals(
                "AuthorizationFailure",
                response.getBody().get("azureErrorCode")
        );
    }

    @Test
    void shouldHandle409BlobOperationException() {

        BlobOperationException ex =
                new BlobOperationException(
                        "Conflict",
                        409,
                        "BlobAlreadyExists"
                );

        ResponseEntity<Map<String, String>> response =
                handler.handleBlobOperationException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(
                "Conflict — the resource may already exist.",
                response.getBody().get("error")
        );

        assertEquals(
                "BlobAlreadyExists",
                response.getBody().get("azureErrorCode")
        );
    }

    @Test
    void shouldHandleGenericBlobOperationException() {

        BlobOperationException ex =
                new BlobOperationException(
                        "Unknown",
                        500,
                        "InternalError"
                );

        ResponseEntity<Map<String, String>> response =
                handler.handleBlobOperationException(ex);

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "An unexpected storage error occurred. See logs for details.",
                response.getBody().get("error")
        );

        assertEquals(
                "InternalError",
                response.getBody().get("azureErrorCode")
        );
    }

    @Test
    void shouldHandleIllegalStateException() {

        IllegalStateException ex =
                new IllegalStateException(
                        "Storage service unavailable"
                );

        ResponseEntity<Map<String, String>> response =
                handler.handleIllegalState(ex);

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                "Storage service unavailable",
                response.getBody().get("error")
        );
    }
}