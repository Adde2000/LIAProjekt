package se.liaprojekt.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

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
            IllegalArgumentException ex,
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

    // 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {

        log.error("500 INTERNAL ERROR | {} {}",
                request.getMethod(),
                request.getRequestURI(),
                ex
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        500,
                        "Internal Server Error",
//                        "Unexpected error occurred",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }
}