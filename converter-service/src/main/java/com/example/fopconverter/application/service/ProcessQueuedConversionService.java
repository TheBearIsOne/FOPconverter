package com.example.fopconverter.application.service;

import com.example.fopconverter.application.dto.QueuedConversionJob;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.in.ProcessQueuedConversionUseCase;
import com.example.fopconverter.application.port.out.ArtifactStoragePort;
import com.example.fopconverter.application.port.out.ConversionEnginePort;
import com.example.fopconverter.application.port.out.JobPayloadStorePort;
import com.example.fopconverter.application.port.out.JobStatusStorePort;
import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProcessQueuedConversionService implements ProcessQueuedConversionUseCase {
    private static final Logger logger = LoggerFactory.getLogger(ProcessQueuedConversionService.class);

    private final ConversionEnginePort conversionEnginePort;
    private final ArtifactStoragePort artifactStoragePort;
    private final JobPayloadStorePort jobPayloadStorePort;
    private final JobStatusStorePort jobStatusStorePort;

    public ProcessQueuedConversionService(
            ConversionEnginePort conversionEnginePort,
            ArtifactStoragePort artifactStoragePort,
            JobPayloadStorePort jobPayloadStorePort,
            JobStatusStorePort jobStatusStorePort) {
        this.conversionEnginePort = conversionEnginePort;
        this.artifactStoragePort = artifactStoragePort;
        this.jobPayloadStorePort = jobPayloadStorePort;
        this.jobStatusStorePort = jobStatusStorePort;
    }

    @Override
    public void process(QueuedConversionJob queuedJob) {
        ConversionJob job = null;
        boolean payloadLoaded = false;

        try {
            ConversionSource source = jobPayloadStorePort.read(queuedJob.payloadLocation());
            payloadLoaded = true;
            job = new ConversionJob(
                    queuedJob.jobId(),
                    source,
                    queuedJob.outputFormat(),
                    queuedJob.createdAt(),
                    queuedJob.correlationId(),
                    JobStatus.PENDING);
            job.markProcessing();
            jobStatusStorePort.save(job);

            var result = conversionEnginePort.convert(job.source(), job.outputFormat(), job.correlationId());
            String location = artifactStoragePort.store(result.fileName(), result.bytes());
            job.markCompleted(location);
            jobStatusStorePort.save(job);
        } catch (ConversionException exception) {
            if (job == null) {
                throw exception;
            }
            job.markFailed(exception.getErrorCode(), exception.getMessage());
            jobStatusStorePort.save(job);
        } catch (RuntimeException exception) {
            if (job == null) {
                throw exception;
            }
            job.markFailed(
                    "JOB_PROCESSING_FAILED",
                    exception.getMessage() == null ? "Unexpected async processing failure" : exception.getMessage());
            jobStatusStorePort.save(job);
        } finally {
            if (payloadLoaded) {
                deletePayloadQuietly(queuedJob.payloadLocation(), queuedJob.jobId());
            }
        }
    }

    private void deletePayloadQuietly(String payloadLocation, java.util.UUID jobId) {
        try {
            jobPayloadStorePort.delete(payloadLocation);
        } catch (RuntimeException exception) {
            logger.warn("Failed to delete queued conversion payload jobId={} location={}", jobId, payloadLocation, exception);
        }
    }
}