package com.example.fopconverter.application.service;

import com.example.fopconverter.domain.model.CacheKey;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.OutputFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class CacheKeyFactory {

    public CacheKey create(ConversionSource source, OutputFormat outputFormat) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(source.primaryDocument().content());
            source.optionalSecondaryDocument().ifPresent(document -> digest.update(document.content()));
            digest.update(source.templateHash().getBytes(StandardCharsets.UTF_8));
            digest.update(outputFormat.name().getBytes(StandardCharsets.UTF_8));
            return new CacheKey(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but unavailable", exception);
        }
    }
}