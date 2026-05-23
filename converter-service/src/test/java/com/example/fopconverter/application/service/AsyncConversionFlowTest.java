package com.example.fopconverter.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fopconverter.application.dto.ConversionCommand;
import com.example.fopconverter.application.dto.QueuedConversionJob;
import com.example.fopconverter.application.dto.SubmitConversionResponse;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.out.ArtifactStoragePort;
import com.example.fopconverter.application.port.out.ConversionEnginePort;
import com.example.fopconverter.application.port.out.JobQueuePort;
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
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import com.example.fopconverter.infrastructure.storage.FileSystemJobPayloadStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsyncConversionFlowTest {

    private static final byte[] CONVERSION_BYTES = "pdf-bytes".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDirectory;

    @Test
    void shouldProcessSubmittedJobAcrossAsyncApplicationServices() {
        ConversionCommandMapper conversionCommandMapper = new ConversionCommandMapper();
        RecordingJobQueue jobQueuePort = new RecordingJobQueue();
        RecordingJobStatusStore jobStatusStore = new RecordingJobStatusStore();
        RecordingArtifactStorage artifactStoragePort = new RecordingArtifactStorage();
        RecordingConversionEngine conversionEnginePort = new RecordingConversionEngine();
        FileSystemJobPayloadStore jobPayloadStorePort = new FileSystemJobPayloadStore(
                conversionProperties(tempDirectory), new ObjectMapper());
        SubmitConversionService submitConversionService = new SubmitConversionService(
                conversionCommandMapper, jobPayloadStorePort, jobQueuePort, jobStatusStore);
        ProcessQueuedConversionService processQueuedConversionService = new ProcessQueuedConversionService(
                conversionEnginePort, artifactStoragePort, jobPayloadStorePort, jobStatusStore);
        ConversionCommand command = sampleCommand();
        ConversionSource expectedSource = conversionCommandMapper.toSource(command);

        SubmitConversionResponse response = submitConversionService.submit(command);

        QueuedConversionJob queuedJob = jobQueuePort.onlyPublishedJob();
        Path payloadPath = tempDirectory.resolve(queuedJob.payloadLocation()).normalize();

        assertThat(response.jobId()).isEqualTo(queuedJob.jobId());
        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.statusUrl()).isEqualTo("/api/v1/jobs/" + queuedJob.jobId());
        assertThat(queuedJob.payloadLocation()).startsWith("requests/");
        assertThat(Path.of(queuedJob.payloadLocation())).isRelative();
        assertThat(Files.exists(payloadPath)).isTrue();
        assertThat(jobStatusStore.statusHistoryFor(queuedJob.jobId())).containsExactly(JobStatus.PENDING);

        processQueuedConversionService.process(queuedJob);

        JobStatusSnapshot snapshot = jobStatusStore.findById(queuedJob.jobId()).orElseThrow();

        assertThat(conversionEnginePort.source()).isEqualTo(expectedSource);
        assertThat(conversionEnginePort.outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(conversionEnginePort.correlationId()).isEqualTo("corr-123");
        assertThat(jobStatusStore.statusHistoryFor(queuedJob.jobId()))
                .containsExactly(JobStatus.PENDING, JobStatus.PROCESSING, JobStatus.COMPLETED);
        assertThat(snapshot.outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(snapshot.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(snapshot.artifactLocation()).isEqualTo("artifacts/conversion.pdf");
        assertThat(snapshot.errorCode()).isNull();
        assertThat(snapshot.errorDetails()).isNull();
        assertThat(artifactStoragePort.read(snapshot.artifactLocation())).isEqualTo(CONVERSION_BYTES);
        assertThat(Files.exists(payloadPath)).isFalse();
    }

    @Test
    void shouldPersistTerminalFailureAndDeletePayloadWhenWorkerProcessingFails() {
        ConversionCommandMapper conversionCommandMapper = new ConversionCommandMapper();
        RecordingJobQueue jobQueuePort = new RecordingJobQueue();
        RecordingJobStatusStore jobStatusStore = new RecordingJobStatusStore();
        RecordingArtifactStorage artifactStoragePort = new RecordingArtifactStorage();
        FailingConversionEngine conversionEnginePort = new FailingConversionEngine();
        FileSystemJobPayloadStore jobPayloadStorePort = new FileSystemJobPayloadStore(
                conversionProperties(tempDirectory), new ObjectMapper());
        SubmitConversionService submitConversionService = new SubmitConversionService(
                conversionCommandMapper, jobPayloadStorePort, jobQueuePort, jobStatusStore);
        ProcessQueuedConversionService processQueuedConversionService = new ProcessQueuedConversionService(
                conversionEnginePort, artifactStoragePort, jobPayloadStorePort, jobStatusStore);
        ConversionCommand command = sampleCommand();
        ConversionSource expectedSource = conversionCommandMapper.toSource(command);

        submitConversionService.submit(command);

        QueuedConversionJob queuedJob = jobQueuePort.onlyPublishedJob();
        Path payloadPath = tempDirectory.resolve(queuedJob.payloadLocation()).normalize();

        assertThat(Files.exists(payloadPath)).isTrue();

        processQueuedConversionService.process(queuedJob);

        JobStatusSnapshot snapshot = jobStatusStore.findById(queuedJob.jobId()).orElseThrow();

        assertThat(conversionEnginePort.source()).isEqualTo(expectedSource);
        assertThat(conversionEnginePort.outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(conversionEnginePort.correlationId()).isEqualTo("corr-123");
        assertThat(jobStatusStore.statusHistoryFor(queuedJob.jobId()))
                .containsExactly(JobStatus.PENDING, JobStatus.PROCESSING, JobStatus.FAILED);
        assertThat(snapshot.outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(snapshot.status()).isEqualTo(JobStatus.FAILED);
        assertThat(snapshot.artifactLocation()).isNull();
        assertThat(snapshot.errorCode()).isEqualTo("FOP_RENDER_FAILED");
        assertThat(snapshot.errorDetails()).isEqualTo("Apache FOP failed");
        assertThat(artifactStoragePort.storedArtifactLocations()).isEmpty();
        assertThat(Files.exists(payloadPath)).isFalse();
    }

    private ConversionProperties conversionProperties(Path artifactDirectory) {
        ConversionProperties properties = new ConversionProperties();
        properties.getStorage().setArtifactDirectory(artifactDirectory.toString());
        return properties;
    }

    private ConversionCommand sampleCommand() {
        return new ConversionCommand(
                InputPayloadType.XML_XSLT,
                new ConversionDocument("sample.xml", "<root/>".getBytes(StandardCharsets.UTF_8)),
                new ConversionDocument(
                        "sample.xslt",
                        "<xsl:stylesheet version=\"1.0\"/>".getBytes(StandardCharsets.UTF_8)),
                OutputFormat.PDF,
                "corr-123");
    }

    private static final class RecordingJobQueue implements JobQueuePort {
        private final List<QueuedConversionJob> publishedJobs = new ArrayList<>();

        @Override
        public void publish(QueuedConversionJob job) {
            publishedJobs.add(job);
        }

        private QueuedConversionJob onlyPublishedJob() {
            assertThat(publishedJobs).hasSize(1);
            return publishedJobs.getFirst();
        }
    }

    private static final class RecordingJobStatusStore implements JobStatusStorePort {
        private final Map<UUID, JobStatusSnapshot> snapshots = new HashMap<>();
        private final Map<UUID, List<JobStatus>> statusHistory = new HashMap<>();

        @Override
        public void save(ConversionJob job) {
            snapshots.put(
                    job.jobId(),
                    new JobStatusSnapshot(
                            job.jobId(),
                            job.outputFormat(),
                            job.status(),
                            job.artifactLocation(),
                            job.errorCode(),
                            job.errorDetails()));
                statusHistory
                    .computeIfAbsent(job.jobId(), ignored -> new ArrayList<>())
                    .add(job.status());
        }

        @Override
        public Optional<JobStatusSnapshot> findById(UUID jobId) {
            return Optional.ofNullable(snapshots.get(jobId));
        }

        private List<JobStatus> statusHistoryFor(UUID jobId) {
            return statusHistory.getOrDefault(jobId, List.of());
        }
    }

    private static final class RecordingArtifactStorage implements ArtifactStoragePort {
        private final Map<String, byte[]> storedArtifacts = new HashMap<>();

        @Override
        public String store(String fileName, byte[] bytes) {
            String location = "artifacts/" + fileName;
            storedArtifacts.put(location, bytes.clone());
            return location;
        }

        @Override
        public byte[] read(String location) {
            return storedArtifacts.get(location).clone();
        }

        private java.util.Set<String> storedArtifactLocations() {
            return storedArtifacts.keySet();
        }
    }

    private static final class RecordingConversionEngine implements ConversionEnginePort {
        private ConversionSource source;
        private OutputFormat outputFormat;
        private String correlationId;

        @Override
        public ConversionResult convert(ConversionSource source, OutputFormat outputFormat, String correlationId) {
            this.source = source;
            this.outputFormat = outputFormat;
            this.correlationId = correlationId;
            return new ConversionResult(
                    CONVERSION_BYTES,
                    OutputFormat.PDF,
                    "conversion.pdf",
                    OutputFormat.PDF.mimeType(),
                    false,
                    25L,
                    new CacheKey("cache-key"));
        }

        private ConversionSource source() {
            return source;
        }

        private OutputFormat outputFormat() {
            return outputFormat;
        }

        private String correlationId() {
            return correlationId;
        }
    }

    private static final class FailingConversionEngine implements ConversionEnginePort {
        private ConversionSource source;
        private OutputFormat outputFormat;
        private String correlationId;

        @Override
        public ConversionResult convert(ConversionSource source, OutputFormat outputFormat, String correlationId) {
            this.source = source;
            this.outputFormat = outputFormat;
            this.correlationId = correlationId;
            throw new ConversionException("FOP_RENDER_FAILED", "Apache FOP failed");
        }

        private ConversionSource source() {
            return source;
        }

        private OutputFormat outputFormat() {
            return outputFormat;
        }

        private String correlationId() {
            return correlationId;
        }
    }
}