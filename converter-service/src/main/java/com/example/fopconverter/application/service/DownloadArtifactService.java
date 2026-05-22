package com.example.fopconverter.application.service;

import com.example.fopconverter.application.dto.SyncConversionResponse;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.in.DownloadArtifactUseCase;
import com.example.fopconverter.application.port.out.ArtifactStoragePort;
import com.example.fopconverter.application.port.out.JobStatusStorePort;
import com.example.fopconverter.domain.model.JobStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DownloadArtifactService implements DownloadArtifactUseCase {
    private final JobStatusStorePort jobStatusStorePort;
    private final ArtifactStoragePort artifactStoragePort;

    public DownloadArtifactService(JobStatusStorePort jobStatusStorePort, ArtifactStoragePort artifactStoragePort) {
        this.jobStatusStorePort = jobStatusStorePort;
        this.artifactStoragePort = artifactStoragePort;
    }

    @Override
    public SyncConversionResponse download(UUID jobId) {
        var job = jobStatusStorePort
                .findById(jobId)
                .orElseThrow(() -> new ConversionException("JOB_NOT_FOUND", "Job not found: " + jobId));
        if (job.status() != JobStatus.COMPLETED || job.artifactLocation() == null) {
            throw new ConversionException("ARTIFACT_NOT_READY", "Artifact is not ready for job: " + jobId);
        }

        byte[] bytes = artifactStoragePort.read(job.artifactLocation());
        String fileName = "conversion-" + jobId + "." + job.outputFormat().fileExtension();
        return new SyncConversionResponse(bytes, fileName, job.outputFormat().mimeType(), 0L);
    }
}