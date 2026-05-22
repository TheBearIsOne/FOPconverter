package com.example.fopconverter.infrastructure.status;

import com.example.fopconverter.application.port.out.JobStatusStorePort;
import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.JobStatus;
import com.example.fopconverter.domain.model.OutputFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisJobStatusStore implements JobStatusStorePort {
    private static final String PREFIX = "converter:jobs:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisJobStatusStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(ConversionJob job) {
        try {
            redisTemplate.opsForValue().set(key(job.jobId()), objectMapper.writeValueAsString(JobRecord.from(job)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to persist job status", exception);
        }
    }

    @Override
    public Optional<ConversionJob> findById(UUID jobId) {
        String raw = redisTemplate.opsForValue().get(key(jobId));
        if (raw == null) {
            return Optional.empty();
        }

        try {
            JobRecord record = objectMapper.readValue(raw, JobRecord.class);
            return Optional.of(record.toDomain());
        } catch (JsonProcessingException exception) {
            redisTemplate.delete(key(jobId));
            return Optional.empty();
        }
    }

    private String key(UUID jobId) {
        return PREFIX + jobId;
    }

    private record JobRecord(
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
            String status,
            String artifactLocation,
            String errorCode,
            String errorDetails) {

        static JobRecord from(ConversionJob job) {
            return new JobRecord(
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
                    job.status().name(),
                    job.artifactLocation(),
                    job.errorCode(),
                    job.errorDetails());
        }

        ConversionJob toDomain() {
            ConversionSource source = new ConversionSource(
                    InputPayloadType.valueOf(payloadType),
                    new ConversionDocument(primaryFileName, Base64.getDecoder().decode(primaryDocumentBase64)),
                    secondaryDocumentBase64 == null
                            ? null
                            : new ConversionDocument(secondaryFileName, Base64.getDecoder().decode(secondaryDocumentBase64)),
                    templateHash);
            ConversionJob job = new ConversionJob(
                    jobId,
                    source,
                    OutputFormat.valueOf(outputFormat),
                    createdAt,
                    correlationId,
                    JobStatus.valueOf(status));
            if (artifactLocation != null) {
                job.markCompleted(artifactLocation);
            }
            if (errorCode != null) {
                job.markFailed(errorCode, errorDetails);
            }
            return job;
        }
    }
}