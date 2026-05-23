package com.example.fopconverter.infrastructure.status;

import com.example.fopconverter.application.port.out.JobStatusStorePort;
import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.domain.model.JobStatus;
import com.example.fopconverter.domain.model.JobStatusSnapshot;
import com.example.fopconverter.domain.model.OutputFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    public Optional<JobStatusSnapshot> findById(UUID jobId) {
        String raw = redisTemplate.opsForValue().get(key(jobId));
        if (raw == null) {
            return Optional.empty();
        }

        try {
            JobRecord jobRecord = objectMapper.readValue(raw, JobRecord.class);
            return Optional.of(jobRecord.toSnapshot());
        } catch (JsonProcessingException exception) {
            redisTemplate.delete(key(jobId));
            return Optional.empty();
        }
    }

    private String key(UUID jobId) {
        return PREFIX + jobId;
    }

    record JobRecord(
            UUID jobId,
            String outputFormat,
            String status,
            String artifactLocation,
            String errorCode,
            String errorDetails) {

        static JobRecord from(ConversionJob job) {
            return new JobRecord(
                    job.jobId(),
                    job.outputFormat().name(),
                    job.status().name(),
                    job.artifactLocation(),
                    job.errorCode(),
                    job.errorDetails());
        }

    JobStatusSnapshot toSnapshot() {
        return new JobStatusSnapshot(
                    jobId,
                    OutputFormat.valueOf(outputFormat),
                    JobStatus.valueOf(status),
                    artifactLocation,
                    errorCode,
                    errorDetails);
        }
    }
}