package com.example.fopconverter.infrastructure.messaging.nats;

import com.example.fopconverter.application.dto.QueuedConversionJob;
import com.example.fopconverter.domain.model.OutputFormat;
import java.time.Instant;
import java.util.UUID;

public record JobEnvelope(
        UUID jobId,
        String outputFormat,
        Instant createdAt,
        String correlationId,
        String payloadLocation) {

    static JobEnvelope from(QueuedConversionJob job) {
        return new JobEnvelope(
                job.jobId(),
                job.outputFormat().name(),
                job.createdAt(),
                job.correlationId(),
                job.payloadLocation());
    }

    QueuedConversionJob toQueuedJob() {
        return new QueuedConversionJob(
                jobId,
                OutputFormat.valueOf(outputFormat),
                createdAt,
                correlationId,
                payloadLocation);
    }
}