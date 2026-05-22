package com.example.fopconverter.application.dto;

import java.util.UUID;

public record SubmitConversionResponse(UUID jobId, String status, String statusUrl) {}