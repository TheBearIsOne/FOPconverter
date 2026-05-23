package com.example.fopconverter.application.dto;

import com.example.fopconverter.domain.model.OutputFormat;
import java.time.Instant;
import java.util.UUID;

public record QueuedConversionJob(
        UUID jobId,
        OutputFormat outputFormat,
        Instant createdAt,
        String correlationId,
        String payloadLocation) {}