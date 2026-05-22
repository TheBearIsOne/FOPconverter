package com.example.fopconverter.infrastructure.fop;

import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.out.ConversionEnginePort;
import com.example.fopconverter.domain.model.CacheKey;
import com.example.fopconverter.domain.model.ConversionResult;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.domain.model.OutputFormat;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class FopConversionService implements ConversionEnginePort {
    private static final Logger log = LoggerFactory.getLogger(FopConversionService.class);

    private final FopFactory fopFactory;
    private final MeterRegistry meterRegistry;
    private final Semaphore semaphore;

    public FopConversionService(
            ResourceLoader resourceLoader,
            MeterRegistry meterRegistry,
            com.example.fopconverter.infrastructure.config.ConversionProperties properties) {
        this.meterRegistry = meterRegistry;
        this.semaphore = new Semaphore(properties.getMaxConcurrentConversions());
        this.fopFactory = initializeFopFactory(resourceLoader);
    }

    @Override
    public ConversionResult convert(ConversionSource source, OutputFormat outputFormat, String correlationId) {
        long startedAt = System.nanoTime();
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            throw new ConversionException("TOO_BUSY", "Too many conversions are in progress");
        }

        try {
            byte[] foBytes = source.payloadType() == InputPayloadType.XSL_FO
                    ? source.primaryDocument().content()
                    : transformToFo(source);
            byte[] resultBytes = switch (outputFormat) {
                case XML -> foBytes;
                case TXT -> toText(foBytes);
                default -> renderWithFop(foBytes, outputFormat, correlationId);
            };
            long durationMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            meterRegistry
                    .timer("fop_conversion_duration_seconds", "format", outputFormat.name().toLowerCase())
                    .record(Duration.ofMillis(durationMillis));
            return new ConversionResult(
                    resultBytes,
                    outputFormat,
                    "conversion-result." + outputFormat.fileExtension(),
                    outputFormat.mimeType(),
                    false,
                    durationMillis,
                    new CacheKey("runtime-generated"));
        } finally {
            semaphore.release();
        }
    }

    private FopFactory initializeFopFactory(ResourceLoader resourceLoader) {
        URI baseUri = Path.of("").toAbsolutePath().normalize().toUri();
        try {
            Resource resource = resourceLoader.getResource("classpath:fop/fop.xconf");
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    return new FopConfParser(inputStream, baseUri)
                            .getFopFactoryBuilder()
                            .build();
                }
            }
            return new FopFactoryBuilder(baseUri).build();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize Apache FOP factory", exception);
        }
    }

    private byte[] transformToFo(ConversionSource source) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Source xmlSource =
                new StreamSource(new ByteArrayInputStream(source.primaryDocument().content()));
            Templates templates = transformerFactory.newTemplates(
                    new StreamSource(new ByteArrayInputStream(source.optionalSecondaryDocument()
                            .orElseThrow(() -> new ConversionException("MISSING_XSLT", "XSLT document is required"))
                            .content())));
            Transformer transformer = templates.newTransformer();
            transformer.transform(xmlSource, new StreamResult(outputStream));
            return outputStream.toByteArray();
        } catch (TransformerConfigurationException exception) {
            throw new ConversionException("INVALID_XSLT", "XSLT configuration is invalid", exception);
        } catch (TransformerException exception) {
            throw new ConversionException("XSLT_TRANSFORMATION_FAILED", "Failed to transform XML with XSLT", exception);
        } catch (Exception exception) {
            throw new ConversionException("TRANSFORMATION_ERROR", "Unexpected XML/XSLT processing error", exception);
        }
    }

    private byte[] renderWithFop(byte[] foBytes, OutputFormat outputFormat, String correlationId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            FOUserAgent userAgent = fopFactory.newFOUserAgent();
            Fop fop = fopFactory.newFop(outputFormat.rendererMimeType(), userAgent, outputStream);
            Transformer identity = transformerFactory.newTransformer();
            Source source = new StreamSource(new ByteArrayInputStream(foBytes));
            Result result = new SAXResult(fop.getDefaultHandler());
            identity.transform(source, result);
            return outputStream.toByteArray();
        } catch (TransformerException | SAXException exception) {
            log.error(
                    "Apache FOP render failed for format {} and correlationId {}",
                    outputFormat,
                    correlationId,
                    exception);
            throw new ConversionException("FOP_RENDER_FAILED", "Apache FOP failed to render the document", exception);
        } catch (Exception exception) {
            log.error(
                    "Unexpected Apache FOP runtime error for format {} and correlationId {}",
                    outputFormat,
                    correlationId,
                    exception);
            throw new ConversionException("FOP_RUNTIME_ERROR", "Unexpected Apache FOP runtime error", exception);
        }
    }

    private byte[] toText(byte[] foBytes) {
        String raw = new String(foBytes, StandardCharsets.UTF_8);
        String text = raw.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.getBytes(StandardCharsets.UTF_8);
    }
}