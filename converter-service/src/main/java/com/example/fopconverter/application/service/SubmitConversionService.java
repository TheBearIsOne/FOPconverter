package com.example.fopconverter.application.service;

import com.example.fopconverter.application.dto.ConversionCommand;
import com.example.fopconverter.application.dto.SubmitConversionResponse;
import com.example.fopconverter.application.port.in.SubmitConversionUseCase;
import com.example.fopconverter.application.port.out.JobQueuePort;
import com.example.fopconverter.application.port.out.JobStatusStorePort;
import com.example.fopconverter.domain.model.ConversionJob;
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
    private final JobQueuePort jobQueuePort;
    private final JobStatusStorePort jobStatusStorePort;

    public SubmitConversionService(
            ConversionCommandMapper conversionCommandMapper,
            JobQueuePort jobQueuePort,
            JobStatusStorePort jobStatusStorePort) {
        this.conversionCommandMapper = conversionCommandMapper;
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
        ConversionJob job = new ConversionJob(
                jobId,
                conversionCommandMapper.toSource(command),
                command.outputFormat(),
                Instant.now(),
                command.correlationId(),
                JobStatus.PENDING);
        jobStatusStorePort.save(job);
        jobQueuePort.publish(job);
        return new SubmitConversionResponse(jobId, JobStatus.PENDING.name().toLowerCase(), "/api/v1/jobs/" + jobId);
    }
}