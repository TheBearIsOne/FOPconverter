package com.example.fopconverter.infrastructure.cache;

import com.example.fopconverter.application.port.out.ConversionCachePort;
import com.example.fopconverter.domain.model.CacheKey;
import com.example.fopconverter.domain.model.ConversionResult;
import com.example.fopconverter.domain.model.OutputFormat;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Base64;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisCacheService implements ConversionCachePort {
    private static final String CACHE_PREFIX = "converter:cache:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConversionProperties properties;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public RedisCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ConversionProperties properties,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.cacheHitCounter = meterRegistry.counter("fop_cache_hits_total");
        this.cacheMissCounter = meterRegistry.counter("fop_cache_misses_total");
    }

    @Override
    public Optional<ConversionResult> get(CacheKey cacheKey) {
        String raw = redisTemplate.opsForValue().get(cacheKey(cacheKey));
        if (raw == null) {
            cacheMissCounter.increment();
            return Optional.empty();
        }

        try {
            CacheEntry entry = objectMapper.readValue(raw, CacheEntry.class);
            cacheHitCounter.increment();
            return Optional.of(new ConversionResult(
                    Base64.getDecoder().decode(entry.bytes()),
                    OutputFormat.from(entry.outputFormat()),
                    entry.fileName(),
                    entry.contentType(),
                    true,
                    entry.durationMillis(),
                    cacheKey));
        } catch (JsonProcessingException exception) {
            redisTemplate.delete(cacheKey(cacheKey));
            cacheMissCounter.increment();
            return Optional.empty();
        }
    }

    @Override
    public void put(CacheKey cacheKey, ConversionResult result) {
        CacheEntry entry = new CacheEntry(
                Base64.getEncoder().encodeToString(result.bytes()),
                result.outputFormat().name(),
                result.fileName(),
                result.contentType(),
                result.durationMillis());
        try {
            redisTemplate
                    .opsForValue()
                    .set(cacheKey(cacheKey), objectMapper.writeValueAsString(entry), properties.getCacheTtl());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize conversion cache entry", exception);
        }
    }

    private String cacheKey(CacheKey cacheKey) {
        return CACHE_PREFIX + cacheKey.value();
    }

    private record CacheEntry(
            String bytes, String outputFormat, String fileName, String contentType, long durationMillis) {}
}