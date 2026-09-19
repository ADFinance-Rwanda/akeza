package com.tenant.management.exception;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception ex) {
        if (ex instanceof ResourceNotFoundException resourceNotFoundException) {
            return build(HttpStatus.NOT_FOUND, resourceNotFoundException.getMessage(), null);
        }
        return build(HttpStatus.NOT_FOUND, "Not found", null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException ex, HttpServletResponse response) {
        if (response.getHeader("Retry-After") == null) {
            response.setHeader("Retry-After", "60");
        }
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), null);
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimistic(org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "The resource was updated by another request. Reload and retry.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            details.put(field, error.getDefaultMessage());
        });
        return build(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnavailable(ServiceUnavailableException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(HttpStatus.BAD_REQUEST, "Missing required header: " + ex.getHeaderName(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + ex.getName(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request body", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, Map<String, String> details) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(status.value())
                .message(message)
                .timestamp(LocalDateTime.now())
                .details(details)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
