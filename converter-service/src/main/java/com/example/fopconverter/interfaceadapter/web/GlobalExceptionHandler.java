package com.example.fopconverter.interfaceadapter.web;

import com.example.fopconverter.application.exception.ConversionException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_INTERNAL_ERROR_MESSAGE = "Unexpected internal server error";
    private static final String ASYNC_UNAVAILABLE_MESSAGE = "Asynchronous conversion is temporarily unavailable";
    private static final String GENERIC_VALIDATION_ERROR_MESSAGE = "Request validation failed";

    @ExceptionHandler(ConversionException.class)
    public ResponseEntity<ApiErrorResponse> handleConversionException(ConversionException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case "JOB_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "TOO_BUSY" -> HttpStatus.TOO_MANY_REQUESTS;
            case "ARTIFACT_NOT_READY" -> HttpStatus.CONFLICT;
            case "SYNC_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "PROCESSING_INTERRUPTED" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "ASYNC_UNAVAILABLE", "JOB_SUBMISSION_FAILED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(exception.getErrorCode(), publicMessage(exception), Instant.now()));
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("VALIDATION_ERROR", validationMessage(exception), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled exception reached API boundary", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", GENERIC_INTERNAL_ERROR_MESSAGE, Instant.now()));
    }

    private String publicMessage(ConversionException exception) {
        return switch (exception.getErrorCode()) {
            case "ASYNC_UNAVAILABLE", "JOB_SUBMISSION_FAILED" -> ASYNC_UNAVAILABLE_MESSAGE;
            default -> exception.getMessage();
        };
    }

    private String validationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException) {
            return GENERIC_VALIDATION_ERROR_MESSAGE;
        }
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? GENERIC_VALIDATION_ERROR_MESSAGE
                : exception.getMessage();
    }
}