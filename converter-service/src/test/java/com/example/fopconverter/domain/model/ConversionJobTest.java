package com.example.fopconverter.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversionJobTest {

    @Test
    void shouldRejectCompletedJobWithoutArtifactWhenRestoring() {
        assertThatThrownBy(() -> ConversionJob.restore(
                        UUID.randomUUID(),
                        sampleSource(),
                        OutputFormat.PDF,
                        Instant.now(),
                        "corr-restore-completed",
                        JobStatus.COMPLETED,
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Completed jobs must have an artifact location");
    }

    @Test
    void shouldRejectTransitionFromCompletedToFailed() {
        ConversionJob job = ConversionJob.restore(
                UUID.randomUUID(),
                sampleSource(),
                OutputFormat.PDF,
                Instant.now(),
                "corr-completed",
                JobStatus.COMPLETED,
                "artifacts/output.pdf",
                null,
                null);

        assertThatThrownBy(() -> job.markFailed("JOB_FAILED", "failure after completion"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending or processing jobs can move to failed");
    }

    @Test
    void shouldAllowPendingToFailForSubmissionErrors() {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(), sampleSource(), OutputFormat.PDF, Instant.now(), "corr-pending", JobStatus.PENDING);

        job.markFailed("ASYNC_UNAVAILABLE", "Asynchronous conversion is disabled");

        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo("ASYNC_UNAVAILABLE");
        assertThat(job.artifactLocation()).isNull();
    }

    @Test
    void shouldAllowRestoringFailedJobWithErrorDetails() {
        assertThatCode(() -> ConversionJob.restore(
                        UUID.randomUUID(),
                        sampleSource(),
                        OutputFormat.PDF,
                        Instant.now(),
                        "corr-restore-failed",
                        JobStatus.FAILED,
                        null,
                        "FOP_RENDER_FAILED",
                        "Apache FOP failed"))
                .doesNotThrowAnyException();
    }

    private ConversionSource sampleSource() {
        return new ConversionSource(
                InputPayloadType.XSL_FO,
                new ConversionDocument("sample.fo", "<root/>".getBytes(StandardCharsets.UTF_8)),
                null,
                "template-hash");
    }
}