package se.liaprojekt.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {

        handler = new GlobalExceptionHandler();

        request = mock(HttpServletRequest.class);

        when(request.getMethod())
                .thenReturn("GET");

        when(request.getRequestURI())
                .thenReturn("/api/test");
    }

    @Test
    void shouldHandleResourceNotFound() {

        ResourceNotFoundException ex =
                new ResourceNotFoundException("Course not found");

        ResponseEntity<ErrorResponse> response =
                handler.handleResourceNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(404, response.getBody().getStatus());
        assertEquals("Not Found", response.getBody().getError());
        assertEquals("Course not found", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
    }

    @Test
    void shouldHandleBadRequest() {

        BadRequestException ex =
                new BadRequestException("Invalid input");

        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequest(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(400, response.getBody().getStatus());
        assertEquals("Bad Request", response.getBody().getError());
        assertEquals("Invalid input", response.getBody().getMessage());
    }

    @Test
    void shouldHandle401() {

        BadCredentialsException ex =
                new BadCredentialsException("Bad credentials");

        ResponseEntity<ErrorResponse> response =
                handler.handle401(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(401, response.getBody().getStatus());
        assertEquals("Unauthorized", response.getBody().getError());
        assertEquals(
                "Authentication required",
                response.getBody().getMessage()
        );
    }

    @Test
    void shouldHandle403() {

        AccessDeniedException ex =
                new AccessDeniedException("Forbidden");

        ResponseEntity<ErrorResponse> response =
                handler.handle403(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        assertNotNull(response.getBody());

        assertEquals(403, response.getBody().getStatus());
        assertEquals("Forbidden", response.getBody().getError());
        assertEquals(
                "You do not have permission to access this resource",
                response.getBody().getMessage()
        );
    }

    @Test
    void shouldHandleEventPublishException() {

        EventPublishException ex =
                new EventPublishException(
                        "Service bus failed",
                        new RuntimeException("Azure Service Bus error")
                );

        ResponseEntity<ErrorResponse> response =
                handler.handleEventPublishException(ex, request);

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(500, response.getBody().getStatus());

        assertEquals(
                "Event Publish Error",
                response.getBody().getError()
        );

        assertEquals(
                "Service bus failed",
                response.getBody().getMessage()
        );
    }

    @Test
    void shouldHandleEmailProcessingException() {

        EmailProcessingException ex =
                new EmailProcessingException(
                        "Email failed",
                        new RuntimeException("SMTP error")
                );

        ResponseEntity<ErrorResponse> response =
                handler.handleEmailProcessingException(ex, request);

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(500, response.getBody().getStatus());

        assertEquals(
                "Email Processing Error",
                response.getBody().getError()
        );

        assertEquals(
                "Email failed",
                response.getBody().getMessage()
        );
    }

    @Test
    void shouldHandleAzureAssistantException() {

        AzureAssistantException ex =
                new AzureAssistantException("Azure OpenAI unavailable");

        ResponseEntity<ErrorResponse> response =
                handler.handleAzureAssistantException(ex, request);

        assertEquals(
                HttpStatus.BAD_GATEWAY,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(502, response.getBody().getStatus());
        assertEquals(
                "Azure OpenAI Error",
                response.getBody().getError()
        );
        assertEquals(
                "Azure OpenAI unavailable",
                response.getBody().getMessage()
        );
    }

    @Test
    void shouldHandleGenericException() {

        Exception ex =
                new Exception("Unexpected failure");

        ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(ex, request);

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(500, response.getBody().getStatus());
        assertEquals(
                "An unexpected error occurred.",
                response.getBody().getError()
        );
        assertEquals(
                "Unexpected failure",
                response.getBody().getMessage()
        );
    }

    @Test
    void shouldReturnNullForIOExceptionClientDisconnect() {

        IOException ex =
                new IOException("Broken pipe");

        ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(ex, request);

        assertNull(response);
    }

    @Test
    void shouldReturnNullForAsyncRequestNotUsableException() {

        AsyncRequestNotUsableException ex =
                new AsyncRequestNotUsableException("Client disconnected");

        ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(ex, request);

        assertNull(response);
    }

    @Test
    void shouldReturnNullForBrokenPipeMessage() {

        Exception ex =
                new Exception("Broken pipe");

        ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(ex, request);

        assertNull(response);
    }
}