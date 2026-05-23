package com.example.fopconverter.infrastructure.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.JobStatus;
import com.example.fopconverter.domain.model.OutputFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisJobStatusStoreTest {

    @Test
        void shouldSerializeOnlyStatusSnapshotFields() throws Exception {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                new ConversionSource(
                        InputPayloadType.XSL_FO,
                        new ConversionDocument("sample.fo", "<root/>".getBytes(StandardCharsets.UTF_8)),
                        null,
                        "template-hash"),
                OutputFormat.PDF,
                Instant.now(),
                "corr-redis",
                JobStatus.PENDING);

        String json = new ObjectMapper().writeValueAsString(RedisJobStatusStore.JobRecord.from(job));

        assertThat(json)
                .contains("\"jobId\"")
                .contains("\"outputFormat\"")
                .doesNotContain("primaryDocumentBase64")
                .doesNotContain("secondaryDocumentBase64")
                .doesNotContain("templateHash")
                .doesNotContain("primaryFileName");
    }
}