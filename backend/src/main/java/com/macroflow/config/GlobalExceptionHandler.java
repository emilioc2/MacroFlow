package com.macroflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Global exception handler that maps exceptions to consistent JSON error envelopes.
 *
 * Error envelope shape:
 * <pre>
 * {
 *   "status": 422,
 *   "error": "Unprocessable Entity",
 *   "message": "Validation failed",
 *   "fields": [{ "field": "provider", "message": "must not be blank" }]
 * }
 * </pre>
 *
 * {@code fields} is null for non-validation errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 422 — Bean Validation failure on a request DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map((FieldError fe) -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "status", 422,
                "error", "Unprocessable Entity",
                "message", "Validation failed",
                "fields", fieldErrors
        ));
    }

    /**
     * 422 — Constraint violation on a method parameter (e.g. {@code @NotEmpty} on a
     * {@code @RequestBody List<...>}). Spring 6 fires {@link HandlerMethodValidationException}
     * for these rather than {@link MethodArgumentNotValidException}, so we need a separate
     * handler to keep the 422 contract consistent.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMethodValidation(HandlerMethodValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "status", 422,
                "error", "Unprocessable Entity",
                "message", "Validation failure"
        ));
    }

    /**
     * 422 — Jackson failed to deserialise the request body (e.g. {@code date_of_birth} is not
     * a valid ISO-8601 date). We map this to 422 rather than Spring's default 400 to keep the
     * error envelope consistent with Bean Validation failures.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "status", 422,
                "error", "Unprocessable Entity",
                "message", "Request body could not be parsed: " + ex.getMostSpecificCause().getMessage()
        ));
    }

    /**
     * Propagates {@link ResponseStatusException} thrown by the service layer (e.g. 404 when a
     * profile is not found). Using the exception's own status code keeps the service layer in
     * control of the HTTP semantics without coupling it to the controller.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "status", ex.getStatusCode().value(),
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason() != null ? ex.getReason() : ex.getMessage()
        ));
    }

    /** 401 — Spring Security authentication failure (e.g. bad credentials, expired token). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        // Do not log the exception message — it may contain token fragments
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "status", 401,
                "error", "Unauthorized",
                "message", "Authentication required"
        ));
    }

    /** 403 — Authenticated but not authorised to access the resource. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", 403,
                "error", "Forbidden",
                "message", "Access denied"
        ));
    }

    /** 500 — Unexpected server error. Log it but return a generic message to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "message", "An unexpected error occurred"
        ));
    }
}
