package com.example.fopconverter.domain.model;

public record ConversionResult(
        byte[] bytes,
        OutputFormat outputFormat,
        String fileName,
        String contentType,
        boolean cacheHit,
        long durationMillis,
        CacheKey cacheKey) {}