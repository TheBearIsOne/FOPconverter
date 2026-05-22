package com.example.fopconverter.infrastructure.messaging.nats;

import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.JobStatus;
import com.example.fopconverter.domain.model.OutputFormat;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record JobEnvelope(
        UUID jobId,
        String payloadType,
        String primaryFileName,
        String primaryDocumentBase64,
        String secondaryFileName,
        String secondaryDocumentBase64,
        String templateHash,
        String outputFormat,
        Instant createdAt,
        String correlationId,
        String status) {

    static JobEnvelope from(ConversionJob job) {
        return new JobEnvelope(
                job.jobId(),
                job.source().payloadType().name(),
                job.source().primaryDocument().fileName(),
                Base64.getEncoder().encodeToString(job.source().primaryDocument().content()),
                job.source().optionalSecondaryDocument().map(ConversionDocument::fileName).orElse(null),
                job.source().optionalSecondaryDocument()
                        .map(document -> Base64.getEncoder().encodeToString(document.content()))
                        .orElse(null),
                job.source().templateHash(),
                job.outputFormat().name(),
                job.createdAt(),
                job.correlationId(),
                job.status().name());
    }

    ConversionJob toDomain() {
        return new ConversionJob(
                jobId,
                new ConversionSource(
                        InputPayloadType.valueOf(payloadType),
                        new ConversionDocument(primaryFileName, Base64.getDecoder().decode(primaryDocumentBase64)),
                        secondaryDocumentBase64 == null
                                ? null
                                : new ConversionDocument(secondaryFileName, Base64.getDecoder().decode(secondaryDocumentBase64)),
                        templateHash),
                OutputFormat.valueOf(outputFormat),
                createdAt,
                correlationId,
                JobStatus.valueOf(status));
    }
}