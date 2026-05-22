package com.example.fopconverter.interfaceadapter.web;

import com.example.fopconverter.application.exception.ConversionException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConversionException.class)
    public ResponseEntity<ApiErrorResponse> handleConversionException(ConversionException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case "JOB_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "TOO_BUSY" -> HttpStatus.TOO_MANY_REQUESTS;
            case "ARTIFACT_NOT_READY" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(exception.getErrorCode(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("VALIDATION_ERROR", exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", exception.getMessage(), Instant.now()));
    }
}