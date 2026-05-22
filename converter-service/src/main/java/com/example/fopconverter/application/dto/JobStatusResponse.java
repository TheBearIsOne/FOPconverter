package com.example.fopconverter.application.dto;

import java.util.UUID;

public record JobStatusResponse(
        UUID jobId,
        String status,
        String downloadUrl,
        String errorCode,
        String errorDetails) {}