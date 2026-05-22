package com.example.fopconverter.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.OutputFormat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CacheKeyFactoryTest {
    private final CacheKeyFactory cacheKeyFactory = new CacheKeyFactory();

    @Test
    void shouldGenerateStableHashForSamePayload() {
        ConversionSource source = new ConversionSource(
                InputPayloadType.XSL_FO,
                new ConversionDocument("sample.fo", "<root/>".getBytes(StandardCharsets.UTF_8)),
                null,
                "template-hash");

        String left = cacheKeyFactory.create(source, OutputFormat.PDF).value();
        String right = cacheKeyFactory.create(source, OutputFormat.PDF).value();

        assertThat(left).isEqualTo(right).hasSize(64);
    }
}