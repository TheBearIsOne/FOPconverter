package com.example.fopconverter.interfaceadapter.web;

import com.example.fopconverter.application.dto.JobStatusResponse;
import com.example.fopconverter.application.dto.SyncConversionResponse;
import com.example.fopconverter.application.port.in.DownloadArtifactUseCase;
import com.example.fopconverter.application.port.in.GetJobStatusUseCase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private final GetJobStatusUseCase getJobStatusUseCase;
    private final DownloadArtifactUseCase downloadArtifactUseCase;

    public JobController(GetJobStatusUseCase getJobStatusUseCase, DownloadArtifactUseCase downloadArtifactUseCase) {
        this.getJobStatusUseCase = getJobStatusUseCase;
        this.downloadArtifactUseCase = downloadArtifactUseCase;
    }

    @GetMapping(path = "/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JobStatusResponse status(@PathVariable UUID jobId) {
        return getJobStatusUseCase.getStatus(jobId);
    }

    @GetMapping(path = "/{jobId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID jobId) {
        SyncConversionResponse response = downloadArtifactUseCase.download(jobId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(response.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(response.fileName(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(response.bytes(), headers, HttpStatus.OK);
    }
}