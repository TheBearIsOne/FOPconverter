package com.example.fopconverter.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fopconverter.application.dto.QueuedConversionJob;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.out.ArtifactStoragePort;
import com.example.fopconverter.application.port.out.ConversionEnginePort;
import com.example.fopconverter.application.port.out.JobPayloadStorePort;
import com.example.fopconverter.application.port.out.JobStatusStorePort;
import com.example.fopconverter.domain.model.CacheKey;
import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.domain.model.ConversionResult;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.JobStatus;
import com.example.fopconverter.domain.model.JobStatusSnapshot;
import com.example.fopconverter.domain.model.OutputFormat;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessQueuedConversionServiceTest {

    private static final String PAYLOAD_LOCATION = "requests/job.json";

    @Test
    void shouldPersistFailedStatusWhenProcessingFails() {
        RecordingJobStatusStore jobStatusStore = new RecordingJobStatusStore();
        RecordingJobPayloadStore jobPayloadStorePort = new RecordingJobPayloadStore();
        ConversionEnginePort conversionEnginePort = (source, outputFormat, correlationId) -> {
            throw new ConversionException("FOP_RENDER_FAILED", "Apache FOP failed");
        };
        ArtifactStoragePort artifactStoragePort = new ArtifactStoragePort() {
            @Override
            public String store(String fileName, byte[] bytes) {
                return "unused";
            }

            @Override
            public byte[] read(String location) {
                return new byte[0];
            }
        };
        ProcessQueuedConversionService service =
                new ProcessQueuedConversionService(
                    conversionEnginePort, artifactStoragePort, jobPayloadStorePort, jobStatusStore);

        service.process(sampleQueuedJob());

        assertThat(jobStatusStore.savedStatuses).containsExactly(JobStatus.PROCESSING, JobStatus.FAILED);
        assertThat(jobStatusStore.savedErrorCodes).containsExactly(null, "FOP_RENDER_FAILED");
        assertThat(jobPayloadStorePort.deletedLocations).containsExactly(PAYLOAD_LOCATION);
    }

    @Test
    void shouldPersistCompletedStatusWhenProcessingSucceeds() {
        RecordingJobStatusStore jobStatusStore = new RecordingJobStatusStore();
        RecordingJobPayloadStore jobPayloadStorePort = new RecordingJobPayloadStore();
        ConversionEnginePort conversionEnginePort = (source, outputFormat, correlationId) -> new ConversionResult(
                "pdf".getBytes(StandardCharsets.UTF_8),
                OutputFormat.PDF,
                "conversion.pdf",
                "application/pdf",
                false,
                42L,
                new CacheKey("cache-key"));
        ArtifactStoragePort artifactStoragePort = new ArtifactStoragePort() {
            @Override
            public String store(String fileName, byte[] bytes) {
                return "artifacts/conversion.pdf";
            }

            @Override
            public byte[] read(String location) {
                return new byte[0];
            }
        };
        ProcessQueuedConversionService service =
                new ProcessQueuedConversionService(
                        conversionEnginePort, artifactStoragePort, jobPayloadStorePort, jobStatusStore);

        service.process(sampleQueuedJob());

        assertThat(jobStatusStore.savedStatuses).containsExactly(JobStatus.PROCESSING, JobStatus.COMPLETED);
        assertThat(jobStatusStore.savedArtifactLocations).containsExactly(null, "artifacts/conversion.pdf");
        assertThat(jobPayloadStorePort.deletedLocations).containsExactly(PAYLOAD_LOCATION);
    }

    private QueuedConversionJob sampleQueuedJob() {
        return new QueuedConversionJob(UUID.randomUUID(), OutputFormat.PDF, Instant.now(), "corr-456", PAYLOAD_LOCATION);
    }

    private ConversionSource sampleSource() {
        return new ConversionSource(
                InputPayloadType.XSL_FO,
                new ConversionDocument("sample.fo", "<root/>".getBytes(StandardCharsets.UTF_8)),
                null,
                "template-hash");
    }

    private final class RecordingJobPayloadStore implements JobPayloadStorePort {
        private final List<String> deletedLocations = new ArrayList<>();

        @Override
        public String store(ConversionSource source) {
            return PAYLOAD_LOCATION;
        }

        @Override
        public ConversionSource read(String location) {
            return sampleSource();
        }

        @Override
        public void delete(String location) {
            deletedLocations.add(location);
        }
    }

    private static final class RecordingJobStatusStore implements JobStatusStorePort {
        private final List<JobStatus> savedStatuses = new ArrayList<>();
        private final List<String> savedErrorCodes = new ArrayList<>();
        private final List<String> savedArtifactLocations = new ArrayList<>();

        @Override
        public void save(ConversionJob job) {
            savedStatuses.add(job.status());
            savedErrorCodes.add(job.errorCode());
            savedArtifactLocations.add(job.artifactLocation());
        }

        @Override
        public Optional<JobStatusSnapshot> findById(UUID jobId) {
            return Optional.empty();
        }
    }
}