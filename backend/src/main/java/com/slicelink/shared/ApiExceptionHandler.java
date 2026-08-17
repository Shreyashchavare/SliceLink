package com.slicelink.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", request);
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(status)
                .header("X-Request-ID", requestId)
                .body(new ApiErrorResponse(Instant.now(), status.value(), code, message, request.getRequestURI(), requestId));
    }
}
