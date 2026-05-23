package com.example.fopconverter.interfaceadapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fopconverter.application.dto.ConvertJsonRequest;
import com.example.fopconverter.application.dto.SubmitConversionResponse;
import com.example.fopconverter.application.dto.SyncConversionResponse;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.in.ConvertSynchronouslyUseCase;
import com.example.fopconverter.application.port.in.SubmitConversionUseCase;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ConvertControllerTest {

    @Test
    void shouldRejectOversizedJsonPayloadBeforeSubmittingUseCase() {
        AtomicInteger submitCalls = new AtomicInteger();
        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            ConvertController controller = new ConvertController(
                    unsupportedSyncUseCase(), submitUseCase(submitCalls), executorService, conversionProperties(8));

            ConvertJsonRequest request = new ConvertJsonRequest(
                    "XSL_FO",
                    "PDF",
                    Base64.getEncoder().encodeToString("0123456789".getBytes(StandardCharsets.UTF_8)),
                    "sample.fo",
                    null,
                    null,
                    null,
                    null);

            assertThatThrownBy(() -> controller.submitJson(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Primary document exceeds max input size");
            assertThat(submitCalls.get()).isZero();
        }
    }

    @Test
    void shouldRejectOversizedMultipartPayloadBeforeSubmittingUseCase() {
        AtomicInteger submitCalls = new AtomicInteger();
        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            ConvertController controller = new ConvertController(
                    unsupportedSyncUseCase(), submitUseCase(submitCalls), executorService, conversionProperties(8));
            MockMultipartFile primaryDocument = new MockMultipartFile(
                    "primaryDocument", "sample.fo", "application/xml", "0123456789".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> controller.submitMultipart(
                            "XSL_FO", "PDF", primaryDocument, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Primary document exceeds max input size");
            assertThat(submitCalls.get()).isZero();
        }
    }

    @Test
    void shouldRejectUnsupportedCallbackUrlInJsonRequest() {
            AtomicInteger submitCalls = new AtomicInteger();
            try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            ConvertController controller = new ConvertController(
                    unsupportedSyncUseCase(), submitUseCase(submitCalls), executorService, conversionProperties(1024));

            ConvertJsonRequest request = new ConvertJsonRequest(
                    "XSL_FO",
                    "PDF",
                    Base64.getEncoder().encodeToString("<root/>".getBytes(StandardCharsets.UTF_8)),
                    "sample.fo",
                    null,
                    null,
                    null,
                    "https://example.test/callback");

            assertThatThrownBy(() -> controller.submitJson(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("callbackUrl is not supported");
            assertThat(submitCalls.get()).isZero();
        }
    }

    @Test
    void shouldRejectUnsupportedCallbackUrlInMultipartRequest() {
            AtomicInteger submitCalls = new AtomicInteger();
            try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            ConvertController controller = new ConvertController(
                    unsupportedSyncUseCase(), submitUseCase(submitCalls), executorService, conversionProperties(1024));
            MockMultipartFile primaryDocument = new MockMultipartFile(
                    "primaryDocument", "sample.fo", "application/xml", "<root/>".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> controller.submitMultipart(
                            "XSL_FO",
                            "PDF",
                            primaryDocument,
                            null,
                            null,
                            "https://example.test/callback"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("callbackUrl is not supported");
            assertThat(submitCalls.get()).isZero();
        }
    }

    @Test
    void shouldFailSyncRequestWhenConversionExceedsConfiguredTimeout() {
        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            ConvertController controller = new ConvertController(
                    slowSyncUseCase(), submitUseCase(new AtomicInteger()), executorService, conversionProperties(1024, 10));
            ConvertJsonRequest request = new ConvertJsonRequest(
                    "XSL_FO",
                    "PDF",
                    Base64.getEncoder().encodeToString("<root/>".getBytes(StandardCharsets.UTF_8)),
                    "sample.fo",
                    null,
                    null,
                    null,
                    null);

            assertThatThrownBy(() -> controller.convertSyncJson(request).join())
                    .isInstanceOf(CompletionException.class)
                    .cause()
                    .isInstanceOf(ConversionException.class)
                    .hasMessage("Synchronous conversion timed out");
        }
    }

    @Test
    void shouldInterruptRunningSyncConversionWhenTimeoutExpires() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            ConvertController controller = new ConvertController(
                    interruptibleSlowSyncUseCase(started, interrupted),
                    submitUseCase(new AtomicInteger()),
                    executorService,
                    conversionProperties(1024, 10));
            ConvertJsonRequest request = new ConvertJsonRequest(
                    "XSL_FO",
                    "PDF",
                    Base64.getEncoder().encodeToString("<root/>".getBytes(StandardCharsets.UTF_8)),
                    "sample.fo",
                    null,
                    null,
                    null,
                    null);

            var responseFuture = controller.convertSyncJson(request);

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(responseFuture::join)
                    .isInstanceOf(CompletionException.class)
                    .cause()
                    .isInstanceOf(ConversionException.class)
                    .hasMessage("Synchronous conversion timed out");
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    private SubmitConversionUseCase submitUseCase(AtomicInteger submitCalls) {
        return command -> {
            submitCalls.incrementAndGet();
            return new SubmitConversionResponse(UUID.randomUUID(), "pending", "/api/v1/jobs/test");
        };
    }

    private ConvertSynchronouslyUseCase unsupportedSyncUseCase() {
        return command -> new SyncConversionResponse(new byte[0], "unused", "application/pdf", 0L);
    }

    private ConversionProperties conversionProperties(int maxInputBytes) {
        return conversionProperties(maxInputBytes, 60_000);
    }

    private ConversionProperties conversionProperties(int maxInputBytes, long syncTimeoutMillis) {
        ConversionProperties properties = new ConversionProperties();
        properties.setMaxInputBytes(maxInputBytes);
        properties.setSyncTimeout(Duration.ofMillis(syncTimeoutMillis));
        return properties;
    }

    private ConvertSynchronouslyUseCase slowSyncUseCase() {
        return command -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while simulating slow conversion", exception);
            }
            return new SyncConversionResponse(new byte[0], "unused", "application/pdf", 0L);
        };
    }

    private ConvertSynchronouslyUseCase interruptibleSlowSyncUseCase(
            CountDownLatch started,
            CountDownLatch interrupted) {
        return command -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while simulating cancellable slow conversion", exception);
            }
            return new SyncConversionResponse(new byte[0], "unused", "application/pdf", 0L);
        };
    }
}