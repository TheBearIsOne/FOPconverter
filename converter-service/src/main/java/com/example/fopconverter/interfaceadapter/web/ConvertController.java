package com.example.fopconverter.interfaceadapter.web;

import com.example.fopconverter.application.dto.ConvertJsonRequest;
import com.example.fopconverter.application.dto.ConversionCommand;
import com.example.fopconverter.application.dto.SubmitConversionResponse;
import com.example.fopconverter.application.dto.SyncConversionResponse;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.in.ConvertSynchronouslyUseCase;
import com.example.fopconverter.application.port.in.SubmitConversionUseCase;
import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.OutputFormat;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import java.io.IOException;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/convert")
public class ConvertController {
    private final ConvertSynchronouslyUseCase convertSynchronouslyUseCase;
    private final SubmitConversionUseCase submitConversionUseCase;
    private final ExecutorService conversionExecutorService;
    private final ConversionProperties conversionProperties;

    public ConvertController(
            ConvertSynchronouslyUseCase convertSynchronouslyUseCase,
            SubmitConversionUseCase submitConversionUseCase,
            ExecutorService conversionExecutorService,
            ConversionProperties conversionProperties) {
        this.convertSynchronouslyUseCase = convertSynchronouslyUseCase;
        this.submitConversionUseCase = submitConversionUseCase;
        this.conversionExecutorService = conversionExecutorService;
        this.conversionProperties = conversionProperties;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SubmitConversionResponse> submitJson(@Valid @org.springframework.web.bind.annotation.RequestBody ConvertJsonRequest request) {
        SubmitConversionResponse response = submitConversionUseCase.submit(toCommand(request));
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SubmitConversionResponse> submitMultipart(
            @RequestParam String payloadType,
            @RequestParam String outputFormat,
            @RequestPart("primaryDocument") MultipartFile primaryDocument,
            @RequestPart(value = "secondaryDocument", required = false) MultipartFile secondaryDocument,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String callbackUrl) {
        SubmitConversionResponse response = submitConversionUseCase.submit(toCommand(
                payloadType,
                outputFormat,
                primaryDocument,
                secondaryDocument,
                correlationId,
                callbackUrl));
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping(path = "/sync", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> convertSyncJson(
            @Valid @org.springframework.web.bind.annotation.RequestBody ConvertJsonRequest request) {
        return executeSync(() -> convertSynchronouslyUseCase.convert(toCommand(request)));
    }

    @PostMapping(path = "/sync", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> convertSyncMultipart(
            @RequestParam String payloadType,
            @RequestParam String outputFormat,
            @RequestPart("primaryDocument") MultipartFile primaryDocument,
            @RequestPart(value = "secondaryDocument", required = false) MultipartFile secondaryDocument,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String callbackUrl) {
        return executeSync(() -> convertSynchronouslyUseCase.convert(toCommand(
                payloadType, outputFormat, primaryDocument, secondaryDocument, correlationId, callbackUrl)));
    }

    private CompletableFuture<ResponseEntity<byte[]>> executeSync(SyncConversionSupplier supplier) {
        CompletableFuture<ResponseEntity<byte[]>> result = new CompletableFuture<>();
        Future<?> runningTask;
        try {
            runningTask = conversionExecutorService.submit(() -> {
                try {
                    result.complete(toBinaryResponse(supplier.get()));
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException exception) {
            return CompletableFuture.<ResponseEntity<byte[]>>failedFuture(exception)
                    .exceptionally(throwable -> {
                        throw translateSyncFailure(throwable);
                    });
        }

        CompletableFuture.delayedExecutor(conversionProperties.getSyncTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .execute(() -> cancelTimedOutTask(result, runningTask));

        return result.exceptionally(throwable -> {
            throw translateSyncFailure(throwable);
        });
    }

    private void cancelTimedOutTask(CompletableFuture<ResponseEntity<byte[]>> result, Future<?> runningTask) {
        if (result.completeExceptionally(new java.util.concurrent.TimeoutException("Synchronous conversion timed out"))) {
            runningTask.cancel(true);
        }
    }

    private ConversionCommand toCommand(ConvertJsonRequest request) {
        rejectUnsupportedCallbackUrl(request.callbackUrl());
        return new ConversionCommand(
                InputPayloadType.valueOf(request.payloadType().toUpperCase()),
            toJsonDocument(request.primaryFileName(), request.primaryDocumentBase64(), "Primary"),
                request.secondaryDocumentBase64() == null
                        ? null
                : toJsonDocument(request.secondaryFileName(), request.secondaryDocumentBase64(), "Secondary"),
                OutputFormat.from(request.outputFormat()),
                request.correlationId() == null ? UUID.randomUUID().toString() : request.correlationId());
    }

    private ConversionCommand toCommand(
            String payloadType,
            String outputFormat,
            MultipartFile primaryDocument,
            MultipartFile secondaryDocument,
            String correlationId,
            String callbackUrl) {
        try {
            rejectUnsupportedCallbackUrl(callbackUrl);
            return new ConversionCommand(
                    InputPayloadType.valueOf(payloadType.toUpperCase()),
                    toMultipartDocument("Primary", primaryDocument),
                    secondaryDocument == null
                            ? null
                            : toMultipartDocument("Secondary", secondaryDocument),
                    OutputFormat.from(outputFormat),
                    correlationId == null ? UUID.randomUUID().toString() : correlationId);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read multipart content", exception);
        }
    }

    private void rejectUnsupportedCallbackUrl(String callbackUrl) {
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            throw new IllegalArgumentException("callbackUrl is not supported");
        }
    }

    private ConversionDocument toJsonDocument(String fileName, String documentBase64, String documentRole) {
        if (estimatedDecodedSize(documentBase64) > conversionProperties.getMaxInputBytes()) {
            throw new IllegalArgumentException(
                    documentRole + " document exceeds max input size of " + conversionProperties.getMaxInputBytes() + " bytes");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(documentBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Base64 content for " + documentRole.toLowerCase() + " document", exception);
        }

        validateDocumentSize(bytes.length, documentRole);
        return new ConversionDocument(fileName, bytes);
    }

    private ConversionDocument toMultipartDocument(String documentRole, MultipartFile document) throws IOException {
        validateDocumentSize(document.getSize(), documentRole);
        return new ConversionDocument(document.getOriginalFilename(), document.getBytes());
    }

    private long estimatedDecodedSize(String documentBase64) {
        int padding = 0;
        if (documentBase64.endsWith("==")) {
            padding = 2;
        } else if (documentBase64.endsWith("=")) {
            padding = 1;
        }
        return ((long) documentBase64.length() * 3 / 4) - padding;
    }

    private void validateDocumentSize(long size, String documentRole) {
        if (size > conversionProperties.getMaxInputBytes()) {
            throw new IllegalArgumentException(
                    documentRole + " document exceeds max input size of " + conversionProperties.getMaxInputBytes() + " bytes");
        }
    }

    private ResponseEntity<byte[]> toBinaryResponse(SyncConversionResponse response) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(response.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(response.fileName(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(response.bytes(), headers, HttpStatus.OK);
    }

    private RuntimeException translateSyncFailure(Throwable throwable) {
        Throwable cause = unwrapCompletionFailure(throwable);
        if (cause instanceof ConversionException conversionException) {
            return conversionException;
        }
        if (cause instanceof java.util.concurrent.TimeoutException) {
            return new ConversionException("SYNC_TIMEOUT", "Synchronous conversion timed out", cause);
        }
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Synchronous conversion failed", cause);
    }

    private Throwable unwrapCompletionFailure(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return unwrapCompletionFailure(completionException.getCause());
        }
        return throwable;
    }

    @FunctionalInterface
    private interface SyncConversionSupplier {
        SyncConversionResponse get();
    }
}