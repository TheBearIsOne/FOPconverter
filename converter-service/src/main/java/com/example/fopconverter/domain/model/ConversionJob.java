package com.example.fopconverter.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ConversionJob {
    private final UUID jobId;
    private final ConversionSource source;
    private final OutputFormat outputFormat;
    private final Instant createdAt;
    private final String correlationId;
    private JobStatus status;
    private String artifactLocation;
    private String errorCode;
    private String errorDetails;

    public ConversionJob(
            UUID jobId,
            ConversionSource source,
            OutputFormat outputFormat,
            Instant createdAt,
            String correlationId,
            JobStatus status) {
        this(jobId, source, outputFormat, createdAt, correlationId, status, null, null, null);
    }

    public static ConversionJob restore(
            UUID jobId,
            ConversionSource source,
            OutputFormat outputFormat,
            Instant createdAt,
            String correlationId,
            JobStatus status,
            String artifactLocation,
            String errorCode,
            String errorDetails) {
        return new ConversionJob(
                jobId,
                source,
                outputFormat,
                createdAt,
                correlationId,
                status,
                artifactLocation,
                errorCode,
                errorDetails);
    }

    private ConversionJob(
            UUID jobId,
            ConversionSource source,
            OutputFormat outputFormat,
            Instant createdAt,
            String correlationId,
            JobStatus status,
            String artifactLocation,
            String errorCode,
            String errorDetails) {
        this.jobId = Objects.requireNonNull(jobId, "jobId cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.outputFormat = Objects.requireNonNull(outputFormat, "outputFormat cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.artifactLocation = artifactLocation;
        this.errorCode = errorCode;
        this.errorDetails = errorDetails;
        validateState();
    }

    public UUID jobId() {
        return jobId;
    }

    public ConversionSource source() {
        return source;
    }

    public OutputFormat outputFormat() {
        return outputFormat;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String correlationId() {
        return correlationId;
    }

    public JobStatus status() {
        return status;
    }

    public String artifactLocation() {
        return artifactLocation;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorDetails() {
        return errorDetails;
    }

    public void markProcessing() {
        requireStatus(JobStatus.PENDING, "Only pending jobs can move to processing");
        this.status = JobStatus.PROCESSING;
    }

    public void markCompleted(String artifactLocation) {
        requireStatus(JobStatus.PROCESSING, "Only processing jobs can move to completed");
        if (artifactLocation == null || artifactLocation.isBlank()) {
            throw new IllegalArgumentException("artifactLocation cannot be blank when completing a job");
        }
        this.status = JobStatus.COMPLETED;
        this.artifactLocation = artifactLocation;
        this.errorCode = null;
        this.errorDetails = null;
    }

    public void markFailed(String errorCode, String errorDetails) {
        if (status != JobStatus.PENDING && status != JobStatus.PROCESSING) {
            throw new IllegalStateException("Only pending or processing jobs can move to failed");
        }
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode cannot be blank when failing a job");
        }
        this.status = JobStatus.FAILED;
        this.artifactLocation = null;
        this.errorCode = errorCode;
        this.errorDetails = errorDetails;
    }

    private void requireStatus(JobStatus expectedStatus, String message) {
        if (status != expectedStatus) {
            throw new IllegalStateException(message);
        }
    }

    private void validateState() {
        switch (status) {
            case PENDING, PROCESSING -> {
                if (artifactLocation != null || errorCode != null || errorDetails != null) {
                    throw new IllegalArgumentException(
                            "Pending and processing jobs cannot have artifact or error details");
                }
            }
            case COMPLETED -> {
                if (artifactLocation == null || artifactLocation.isBlank()) {
                    throw new IllegalArgumentException("Completed jobs must have an artifact location");
                }
                if (errorCode != null || errorDetails != null) {
                    throw new IllegalArgumentException("Completed jobs cannot carry error details");
                }
            }
            case FAILED -> {
                if (artifactLocation != null) {
                    throw new IllegalArgumentException("Failed jobs cannot have an artifact location");
                }
                if (errorCode == null || errorCode.isBlank()) {
                    throw new IllegalArgumentException("Failed jobs must have an error code");
                }
            }
        }
    }
}