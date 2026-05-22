package com.example.fopconverter.application.dto;

import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.OutputFormat;

public record ConversionCommand(
        InputPayloadType payloadType,
        ConversionDocument primaryDocument,
        ConversionDocument secondaryDocument,
        OutputFormat outputFormat,
        String correlationId,
        String callbackUrl) {}