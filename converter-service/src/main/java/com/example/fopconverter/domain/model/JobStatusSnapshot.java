package com.example.fopconverter.domain.model;

import java.util.UUID;

public record JobStatusSnapshot(
        UUID jobId,
        OutputFormat outputFormat,
        JobStatus status,
        String artifactLocation,
        String errorCode,
        String errorDetails) {}