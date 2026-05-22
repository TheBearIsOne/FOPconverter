package com.example.fopconverter.application.port.in;

import com.example.fopconverter.application.dto.JobStatusResponse;
import java.util.UUID;

public interface GetJobStatusUseCase {
    JobStatusResponse getStatus(UUID jobId);
}