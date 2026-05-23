package com.example.fopconverter.interfaceadapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fopconverter.application.exception.ConversionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHideUnexpectedExceptionMessage() {
        var response = handler.handleUnexpected(new RuntimeException("jdbc connection leaked details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Unexpected internal server error");
    }

    @Test
    void shouldKeepHelpfulValidationMessageForIllegalArgumentException() {
        var response = handler.handleBadRequest(new IllegalArgumentException("Primary document exceeds max input size of 8 bytes"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Primary document exceeds max input size of 8 bytes");
    }

    @Test
    void shouldHidePublisherFailureMessageBehindStableAsyncUnavailableMessage() {
        var response = handler.handleConversionException(
                new ConversionException("JOB_SUBMISSION_FAILED", "Failed to publish job to async queue"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("JOB_SUBMISSION_FAILED");
        assertThat(response.getBody().message()).isEqualTo("Asynchronous conversion is temporarily unavailable");
    }

    @Test
    void shouldMapSyncTimeoutToGatewayTimeout() {
        var response = handler.handleConversionException(
                new ConversionException("SYNC_TIMEOUT", "Synchronous conversion timed out"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("SYNC_TIMEOUT");
        assertThat(response.getBody().message()).isEqualTo("Synchronous conversion timed out");
    }

    @Test
    void shouldMapProcessingInterruptedToServiceUnavailable() {
        var response = handler.handleConversionException(
                new ConversionException("PROCESSING_INTERRUPTED", "Conversion was interrupted"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("PROCESSING_INTERRUPTED");
        assertThat(response.getBody().message()).isEqualTo("Conversion was interrupted");
    }
}