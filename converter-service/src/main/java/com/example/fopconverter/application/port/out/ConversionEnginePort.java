package com.example.fopconverter.application.port.out;

import com.example.fopconverter.domain.model.ConversionResult;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.OutputFormat;

public interface ConversionEnginePort {
    ConversionResult convert(ConversionSource source, OutputFormat outputFormat, String correlationId);
}