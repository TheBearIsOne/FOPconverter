package com.example.fopconverter.application.service;

import com.example.fopconverter.application.dto.ConversionCommand;
import com.example.fopconverter.application.dto.QueuedConversionJob;
import com.example.fopconverter.application.dto.SubmitConversionResponse;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.in.SubmitConversionUseCase;
import com.example.fopconverter.application.port.out.JobPayloadStorePort;
import com.example.fopconverter.application.port.out.JobQueuePort;
import com.example.fopconverter.application.port.out.JobStatusStorePort;
import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.JobStatus;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SubmitConversionService implements SubmitConversionUseCase {
    private static final Logger logger = LoggerFactory.getLogger(SubmitConversionService.class);

    private final ConversionCommandMapper conversionCommandMapper;
    private final JobPayloadStorePort jobPayloadStorePort;
    private final JobQueuePort jobQueuePort;
    private final JobStatusStorePort jobStatusStorePort;

    public SubmitConversionService(
            ConversionCommandMapper conversionCommandMapper,
            JobPayloadStorePort jobPayloadStorePort,
            JobQueuePort jobQueuePort,
            JobStatusStorePort jobStatusStorePort) {
        this.conversionCommandMapper = conversionCommandMapper;
        this.jobPayloadStorePort = jobPayloadStorePort;
        this.jobQueuePort = jobQueuePort;
        this.jobStatusStorePort = jobStatusStorePort;
    }

    @Override
    public SubmitConversionResponse submit(ConversionCommand command) {
        UUID jobId = UUID.randomUUID();
        logger.info(
                "Accepted async conversion request jobId={} correlationId={} payloadType={} outputFormat={}",
                jobId,
                command.correlationId(),
                command.payloadType(),
                command.outputFormat());
        ConversionSource source = conversionCommandMapper.toSource(command);
        ConversionJob job = new ConversionJob(
                jobId,
                source,
                command.outputFormat(),
                Instant.now(),
                command.correlationId(),
                JobStatus.PENDING);
        jobStatusStorePort.save(job);
        try {
            String payloadLocation = jobPayloadStorePort.store(source);
            jobQueuePort.publish(new QueuedConversionJob(
                    job.jobId(), job.outputFormat(), job.createdAt(), job.correlationId(), payloadLocation));
        } catch (RuntimeException exception) {
            handleSubmissionFailure(job, exception);
            throw exception;
        }
        return new SubmitConversionResponse(jobId, JobStatus.PENDING.name().toLowerCase(), "/api/v1/jobs/" + jobId);
    }

    private void handleSubmissionFailure(ConversionJob job, RuntimeException exception) {
        String errorCode = exception instanceof ConversionException conversionException
                ? conversionException.getErrorCode()
                : "JOB_SUBMISSION_FAILED";
        String errorDetails = exception.getMessage() == null
                ? "Failed to queue conversion job"
                : exception.getMessage();

        logger.error(
                "Failed to submit async conversion job jobId={} correlationId={}",
                job.jobId(),
                job.correlationId(),
                exception);
        job.markFailed(errorCode, errorDetails);
        try {
            jobStatusStorePort.save(job);
        } catch (RuntimeException saveException) {
            logger.error("Failed to persist failed async job status jobId={}", job.jobId(), saveException);
            exception.addSuppressed(saveException);
        }
    }
}