package com.example.fopconverter.application.service;

import com.example.fopconverter.application.dto.ConversionCommand;
import com.example.fopconverter.application.dto.SyncConversionResponse;
import com.example.fopconverter.application.port.in.ConvertSynchronouslyUseCase;
import com.example.fopconverter.application.port.out.ConversionCachePort;
import com.example.fopconverter.application.port.out.ConversionEnginePort;
import com.example.fopconverter.domain.model.CacheKey;
import com.example.fopconverter.domain.model.ConversionResult;
import com.example.fopconverter.domain.model.ConversionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConvertSynchronouslyService implements ConvertSynchronouslyUseCase {
    private static final Logger logger = LoggerFactory.getLogger(ConvertSynchronouslyService.class);

    private final ConversionEnginePort conversionEnginePort;
    private final ConversionCachePort conversionCachePort;
    private final CacheKeyFactory cacheKeyFactory;
    private final ConversionCommandMapper conversionCommandMapper;

    public ConvertSynchronouslyService(
            ConversionEnginePort conversionEnginePort,
            ConversionCachePort conversionCachePort,
            CacheKeyFactory cacheKeyFactory,
            ConversionCommandMapper conversionCommandMapper) {
        this.conversionEnginePort = conversionEnginePort;
        this.conversionCachePort = conversionCachePort;
        this.cacheKeyFactory = cacheKeyFactory;
        this.conversionCommandMapper = conversionCommandMapper;
    }

    @Override
    public SyncConversionResponse convert(ConversionCommand command) {
        logger.info(
            "Processing sync conversion request correlationId={} payloadType={} outputFormat={}",
            command.correlationId(),
            command.payloadType(),
            command.outputFormat());
        ConversionSource source = conversionCommandMapper.toSource(command);
        CacheKey cacheKey = cacheKeyFactory.create(source, command.outputFormat());
        ConversionResult cached = conversionCachePort.get(cacheKey).orElse(null);
        if (cached != null) {
            return new SyncConversionResponse(
                    cached.bytes(), cached.fileName(), cached.contentType(), cached.durationMillis());
        }

        ConversionResult result = conversionEnginePort.convert(source, command.outputFormat(), command.correlationId());
        conversionCachePort.put(cacheKey, result);
        return new SyncConversionResponse(
                result.bytes(), result.fileName(), result.contentType(), result.durationMillis());
    }
}