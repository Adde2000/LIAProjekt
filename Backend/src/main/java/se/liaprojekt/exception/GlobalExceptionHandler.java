package se.liaprojekt.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {

        log.warn("404 NOT FOUND | {} {} | {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        404,
                        "Not Found",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // 400
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex,
            HttpServletRequest request
    ) {

        log.warn("400 BAD REQUEST | {} {} | {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        400,
                        "Bad Request",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // 401
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handle401(
            AuthenticationException ex,
            HttpServletRequest request
    ) {

        log.warn("401 UNAUTHORIZED | {} {}",
                request.getMethod(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        401,
                        "Unauthorized",
                        "Authentication required",
                        request.getRequestURI()
                ));
    }

    // 403
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handle403(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {

        log.warn("403 FORBIDDEN | {} {}",
                request.getMethod(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(
                        403,
                        "Forbidden",
                        "You do not have permission to access this resource",
                        request.getRequestURI()
                ));
    }

    // 500 - Service Bus publish failed
    @ExceptionHandler(EventPublishException.class)
    public ResponseEntity<ErrorResponse> handleEventPublishException(
            EventPublishException ex,
            HttpServletRequest request
    ) {

        log.error("500 EVENT PUBLISH ERROR | {} {} | {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        500,
                        "Event Publish Error",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // 500
    @ExceptionHandler(EmailProcessingException.class)
    public ResponseEntity<ErrorResponse> handleEmailProcessingException(
            EmailProcessingException ex,
            HttpServletRequest request
    ) {

        log.error("500 EMAIL PROCESSING ERROR | {} {} | {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        500,
                        "Email Processing Error",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        if (isClientDisconnect(ex)) {
            log.debug("Client disconnected during streaming: {}", ex.getMessage());
            return null;
        }

        log.error("{} | {} {}", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                request.getMethod(), request.getRequestURI(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "An unexpected error occurred.",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    private boolean isClientDisconnect(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof IOException) return true;
            if (current instanceof AsyncRequestNotUsableException) return true;
            // Matches the Swedish/any locale broken pipe message as a last resort
            if (current.getMessage() != null &&
                    current.getMessage().toLowerCase().contains("broken pipe")) return true;
            current = current.getCause();
        }
        return false;
    }
}