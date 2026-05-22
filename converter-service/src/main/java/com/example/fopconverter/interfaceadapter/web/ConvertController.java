package com.example.fopconverter.interfaceadapter.web;

import com.example.fopconverter.application.dto.ConvertJsonRequest;
import com.example.fopconverter.application.dto.ConversionCommand;
import com.example.fopconverter.application.dto.SubmitConversionResponse;
import com.example.fopconverter.application.dto.SyncConversionResponse;
import com.example.fopconverter.application.port.in.ConvertSynchronouslyUseCase;
import com.example.fopconverter.application.port.in.SubmitConversionUseCase;
import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.OutputFormat;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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

    public ConvertController(
            ConvertSynchronouslyUseCase convertSynchronouslyUseCase,
            SubmitConversionUseCase submitConversionUseCase,
            ExecutorService conversionExecutorService) {
        this.convertSynchronouslyUseCase = convertSynchronouslyUseCase;
        this.submitConversionUseCase = submitConversionUseCase;
        this.conversionExecutorService = conversionExecutorService;
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
        return CompletableFuture.supplyAsync(() -> toBinaryResponse(convertSynchronouslyUseCase.convert(toCommand(request))), conversionExecutorService);
    }

    @PostMapping(path = "/sync", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> convertSyncMultipart(
            @RequestParam String payloadType,
            @RequestParam String outputFormat,
            @RequestPart("primaryDocument") MultipartFile primaryDocument,
            @RequestPart(value = "secondaryDocument", required = false) MultipartFile secondaryDocument,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String callbackUrl) {
        return CompletableFuture.supplyAsync(
                () -> toBinaryResponse(convertSynchronouslyUseCase.convert(toCommand(
                        payloadType, outputFormat, primaryDocument, secondaryDocument, correlationId, callbackUrl))),
                conversionExecutorService);
    }

    private ConversionCommand toCommand(ConvertJsonRequest request) {
        return new ConversionCommand(
                InputPayloadType.valueOf(request.payloadType().toUpperCase()),
                new ConversionDocument(request.primaryFileName(), Base64.getDecoder().decode(request.primaryDocumentBase64())),
                request.secondaryDocumentBase64() == null
                        ? null
                        : new ConversionDocument(
                                request.secondaryFileName(), Base64.getDecoder().decode(request.secondaryDocumentBase64())),
                OutputFormat.from(request.outputFormat()),
                request.correlationId() == null ? UUID.randomUUID().toString() : request.correlationId(),
                request.callbackUrl());
    }

    private ConversionCommand toCommand(
            String payloadType,
            String outputFormat,
            MultipartFile primaryDocument,
            MultipartFile secondaryDocument,
            String correlationId,
            String callbackUrl) {
        try {
            return new ConversionCommand(
                    InputPayloadType.valueOf(payloadType.toUpperCase()),
                    new ConversionDocument(primaryDocument.getOriginalFilename(), primaryDocument.getBytes()),
                    secondaryDocument == null
                            ? null
                            : new ConversionDocument(secondaryDocument.getOriginalFilename(), secondaryDocument.getBytes()),
                    OutputFormat.from(outputFormat),
                    correlationId == null ? UUID.randomUUID().toString() : correlationId,
                    callbackUrl);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to read multipart content", exception);
        }
    }

    private ResponseEntity<byte[]> toBinaryResponse(SyncConversionResponse response) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(response.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(response.fileName(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(response.bytes(), headers, HttpStatus.OK);
    }
}