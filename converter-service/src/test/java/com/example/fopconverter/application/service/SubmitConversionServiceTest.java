package com.example.fopconverter.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fopconverter.application.dto.ConversionCommand;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.out.JobPayloadStorePort;
import com.example.fopconverter.application.port.out.JobQueuePort;
import com.example.fopconverter.application.port.out.JobStatusStorePort;
import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.JobStatus;
import com.example.fopconverter.domain.model.JobStatusSnapshot;
import com.example.fopconverter.domain.model.OutputFormat;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubmitConversionServiceTest {

    private static final String PAYLOAD_LOCATION = "requests/job.json";

    @Test
    void shouldPersistFailedStatusWhenQueuePublishFails() {
        RecordingJobStatusStore jobStatusStore = new RecordingJobStatusStore();
        JobPayloadStorePort jobPayloadStorePort = new JobPayloadStorePort() {
            @Override
            public String store(com.example.fopconverter.domain.model.ConversionSource source) {
                return PAYLOAD_LOCATION;
            }

            @Override
            public com.example.fopconverter.domain.model.ConversionSource read(String location) {
                throw new UnsupportedOperationException("read should not be called in submit flow test");
            }

            @Override
            public void delete(String location) {
                throw new UnsupportedOperationException("delete should not be called in submit flow test");
            }
        };
        JobQueuePort jobQueuePort = job -> {
            throw new ConversionException("ASYNC_UNAVAILABLE", "Asynchronous conversion is disabled");
        };
        SubmitConversionService service =
            new SubmitConversionService(
                new ConversionCommandMapper(), jobPayloadStorePort, jobQueuePort, jobStatusStore);

        assertThatThrownBy(() -> service.submit(sampleCommand()))
                .isInstanceOf(ConversionException.class)
                .hasMessage("Asynchronous conversion is disabled");

        assertThat(jobStatusStore.savedStatuses).containsExactly(JobStatus.PENDING, JobStatus.FAILED);
        assertThat(jobStatusStore.savedErrorCodes).containsExactly(null, "ASYNC_UNAVAILABLE");
    }

    private ConversionCommand sampleCommand() {
        return new ConversionCommand(
                InputPayloadType.XSL_FO,
                new ConversionDocument("sample.fo", "<root/>".getBytes(StandardCharsets.UTF_8)),
                null,
                OutputFormat.PDF,
                "corr-123");
    }

    private static final class RecordingJobStatusStore implements JobStatusStorePort {
        private final List<JobStatus> savedStatuses = new ArrayList<>();
        private final List<String> savedErrorCodes = new ArrayList<>();

        @Override
        public void save(ConversionJob job) {
            savedStatuses.add(job.status());
            savedErrorCodes.add(job.errorCode());
        }

        @Override
        public Optional<JobStatusSnapshot> findById(UUID jobId) {
            return Optional.empty();
        }
    }
}