package com.example.fopconverter.infrastructure.fop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.OutputFormat;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class FopConversionServiceTest {

    @Test
    void shouldBlockExternalXsltInclude() throws Exception {
        Path externalXslt = Files.createTempFile("external-template", ".xslt");
        Files.writeString(externalXslt, "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>",
                StandardCharsets.UTF_8);
        FopConversionService service = new FopConversionService(
                new DefaultResourceLoader(), new SimpleMeterRegistry(), new ConversionProperties());

        String xslt = """
                <xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">
                    <xsl:include href=\"%s\"/>
                    <xsl:template match=\"/\">
                        <root/>
                    </xsl:template>
                </xsl:stylesheet>
                """.formatted(externalXslt.toUri());
        ConversionSource source = new ConversionSource(
                InputPayloadType.XML_XSLT,
                new ConversionDocument("sample.xml", "<root/>".getBytes(StandardCharsets.UTF_8)),
                new ConversionDocument("sample.xslt", xslt.getBytes(StandardCharsets.UTF_8)),
                "template-hash");

        assertThatThrownBy(() -> service.convert(source, OutputFormat.XML, "corr-secure-xslt"))
                .isInstanceOf(ConversionException.class)
                .extracting(throwable -> ((ConversionException) throwable).getErrorCode())
                .isEqualTo("EXTERNAL_RESOURCE_BLOCKED");
    }

    @Test
    void shouldBlockExternalFileUriInSecureFopResourceResolver() throws Exception {
        Path externalImage = Files.createTempFile("external-image", ".png");
        Files.write(externalImage, new byte[] {1, 2, 3});

        assertThatThrownBy(() -> FopConversionService.createSecureResourceResolver(List.of(Path.of("fonts")))
                        .getResource(externalImage.toUri()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("External resource access is not allowed");
    }

    @Test
    void shouldAllowFontFileFromTrustedFontDirectory() throws Exception {
        Path fontsDirectory = Files.createTempDirectory("trusted-fonts");
        Path fontFile = fontsDirectory.resolve("test-font.ttf");
        Files.write(fontFile, new byte[] {1, 2, 3});

        assertThat(FopConversionService.createSecureResourceResolver(List.of(fontsDirectory))
                        .getResource(fontFile.toUri()))
                .isNotNull();
    }

    @Test
    void shouldAllowInlineXmlXsltTransformation() {
        FopConversionService service = new FopConversionService(
                new DefaultResourceLoader(), new SimpleMeterRegistry(), new ConversionProperties());

        String xslt = """
                <xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">
                    <xsl:template match=\"/\">
                        <fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">
                            <fo:layout-master-set>
                                <fo:simple-page-master master-name=\"simple\" page-height=\"11in\" page-width=\"8.5in\" margin=\"1in\">
                                    <fo:region-body/>
                                </fo:simple-page-master>
                            </fo:layout-master-set>
                            <fo:page-sequence master-reference=\"simple\">
                                <fo:flow flow-name=\"xsl-region-body\">
                                    <fo:block>Hello secure world</fo:block>
                                </fo:flow>
                            </fo:page-sequence>
                        </fo:root>
                    </xsl:template>
                </xsl:stylesheet>
                """;
        ConversionSource source = new ConversionSource(
                InputPayloadType.XML_XSLT,
                new ConversionDocument("sample.xml", "<root/>".getBytes(StandardCharsets.UTF_8)),
                new ConversionDocument("sample.xslt", xslt.getBytes(StandardCharsets.UTF_8)),
                "template-hash");

        byte[] result = service.convert(source, OutputFormat.XML, "corr-inline-xslt").bytes();

        assertThat(new String(result, StandardCharsets.UTF_8)).contains("Hello secure world");
    }

    @Test
    void shouldFailFastWhenCurrentThreadIsAlreadyInterrupted() {
        FopConversionService service = new FopConversionService(
                new DefaultResourceLoader(), new SimpleMeterRegistry(), new ConversionProperties());
        ConversionSource source = new ConversionSource(
                InputPayloadType.XSL_FO,
                new ConversionDocument("sample.fo", "<root/>".getBytes(StandardCharsets.UTF_8)),
                null,
                "template-hash");

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> service.convert(source, OutputFormat.XML, "corr-interrupted"))
                    .isInstanceOf(ConversionException.class)
                    .extracting(throwable -> ((ConversionException) throwable).getErrorCode())
                    .isEqualTo("PROCESSING_INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}