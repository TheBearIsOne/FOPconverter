package com.example.fopconverter.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConversionSourceTest {

    @Test
    void shouldRequireSecondaryDocumentForXmlXsltPayload() {
        assertThatThrownBy(() -> new ConversionSource(
                        InputPayloadType.XML_XSLT,
                        new ConversionDocument("sample.xml", "<root/>".getBytes(StandardCharsets.UTF_8)),
                        null,
                        "template-hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Secondary XSLT document is required for XML_XSLT payloads");
    }

    @Test
    void shouldRejectSecondaryDocumentForXslFoPayload() {
        assertThatThrownBy(() -> new ConversionSource(
                        InputPayloadType.XSL_FO,
                        new ConversionDocument("sample.fo", "<root/>".getBytes(StandardCharsets.UTF_8)),
                        new ConversionDocument("unused.xslt", "x".getBytes(StandardCharsets.UTF_8)),
                        "template-hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Secondary document is not supported for XSL_FO payloads");
    }

    @Test
    void shouldAcceptValidXmlXsltPayload() {
        assertThatCode(() -> new ConversionSource(
                        InputPayloadType.XML_XSLT,
                        new ConversionDocument("sample.xml", "<root/>".getBytes(StandardCharsets.UTF_8)),
                        new ConversionDocument("sample.xslt", "<xsl:stylesheet/>".getBytes(StandardCharsets.UTF_8)),
                        "template-hash"))
                .doesNotThrowAnyException();
    }
}