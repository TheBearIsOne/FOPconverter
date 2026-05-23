package com.example.fopconverter.application.service;

import com.example.fopconverter.application.dto.JobStatusResponse;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.in.GetJobStatusUseCase;
import com.example.fopconverter.application.port.out.JobStatusStorePort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetJobStatusService implements GetJobStatusUseCase {
    private final JobStatusStorePort jobStatusStorePort;

    public GetJobStatusService(JobStatusStorePort jobStatusStorePort) {
        this.jobStatusStorePort = jobStatusStorePort;
    }

    @Override
    public JobStatusResponse getStatus(UUID jobId) {
        return jobStatusStorePort
                .findById(jobId)
            .map(jobStatus -> new JobStatusResponse(
                jobStatus.jobId(),
                jobStatus.status().name().toLowerCase(),
                jobStatus.artifactLocation() == null
                    ? null
                    : "/api/v1/jobs/" + jobStatus.jobId() + "/download",
                jobStatus.errorCode(),
                jobStatus.errorDetails()))
                .orElseThrow(() -> new ConversionException("JOB_NOT_FOUND", "Job not found: " + jobId));
    }
}