package com.example.fopconverter.application.dto;

public record SyncConversionResponse(byte[] bytes, String fileName, String contentType, long durationMillis) {}