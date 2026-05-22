package com.example.fopconverter.application.port.out;

import com.example.fopconverter.domain.model.CacheKey;
import com.example.fopconverter.domain.model.ConversionResult;
import java.util.Optional;

public interface ConversionCachePort {
    Optional<ConversionResult> get(CacheKey cacheKey);

    void put(CacheKey cacheKey, ConversionResult result);
}