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
        this.jobId = Objects.requireNonNull(jobId, "jobId cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.outputFormat = Objects.requireNonNull(outputFormat, "outputFormat cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
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
        this.status = JobStatus.PROCESSING;
    }

    public void markCompleted(String artifactLocation) {
        this.status = JobStatus.COMPLETED;
        this.artifactLocation = artifactLocation;
        this.errorCode = null;
        this.errorDetails = null;
    }

    public void markFailed(String errorCode, String errorDetails) {
        this.status = JobStatus.FAILED;
        this.errorCode = errorCode;
        this.errorDetails = errorDetails;
    }
}