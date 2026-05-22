package com.example.fopconverter.application.dto;

import jakarta.validation.constraints.NotBlank;

public record ConvertJsonRequest(
        @NotBlank String payloadType,
        @NotBlank String outputFormat,
        @NotBlank String primaryDocumentBase64,
        String primaryFileName,
        String secondaryDocumentBase64,
        String secondaryFileName,
        String correlationId,
        String callbackUrl) {}