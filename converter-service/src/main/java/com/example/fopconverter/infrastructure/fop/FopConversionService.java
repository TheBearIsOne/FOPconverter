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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.io.ResourceResolverFactory;
import org.apache.xmlgraphics.io.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class FopConversionService implements ConversionEnginePort {
    private static final Logger log = LoggerFactory.getLogger(FopConversionService.class);
    private static final String BLOCKED_RESOURCE_MESSAGE = "External resource access is not allowed";
    private static final String INTERRUPTED_CONVERSION_MESSAGE = "Conversion was interrupted";

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
        throwIfInterrupted();
        long startedAt = System.nanoTime();
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            throw new ConversionException("TOO_BUSY", "Too many conversions are in progress");
        }

        try {
            throwIfInterrupted();
            byte[] foBytes = source.payloadType() == InputPayloadType.XSL_FO
                    ? source.primaryDocument().content()
                    : transformToFo(source);
            throwIfInterrupted();
            byte[] resultBytes = switch (outputFormat) {
                case XML -> foBytes;
                case TXT -> toText(foBytes);
                default -> renderWithFop(foBytes, outputFormat, correlationId);
            };
            throwIfInterrupted();
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
        Path basePath = Path.of("").toAbsolutePath().normalize();
        URI baseUri = basePath.toUri();
        ResourceResolver resourceResolver = createSecureResourceResolver(trustedFontDirectories(basePath));
        try {
            Resource resource = resourceLoader.getResource("classpath:fop/fop.xconf");
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    return new FopConfParser(inputStream, baseUri, resourceResolver)
                            .getFopFactoryBuilder()
                            .build();
                }
            }
            return new FopFactoryBuilder(baseUri, resourceResolver).build();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize Apache FOP factory", exception);
        }
    }

    private byte[] transformToFo(ConversionSource source) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            throwIfInterrupted();
            TransformerFactory transformerFactory = createSecureTransformerFactory();
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
            if (isInterrupted(exception)) {
                throw interruptedConversionException(exception);
            }
            if (isBlockedResourceAccess(exception)) {
                throw new ConversionException(
                        "EXTERNAL_RESOURCE_BLOCKED",
                        "External XML/XSLT resources are not allowed",
                        exception);
            }
            throw new ConversionException("INVALID_XSLT", "XSLT configuration is invalid", exception);
        } catch (TransformerException exception) {
            if (isInterrupted(exception)) {
                throw interruptedConversionException(exception);
            }
            if (isBlockedResourceAccess(exception)) {
                throw new ConversionException(
                        "EXTERNAL_RESOURCE_BLOCKED",
                        "External XML/XSLT resources are not allowed",
                        exception);
            }
            throw new ConversionException("XSLT_TRANSFORMATION_FAILED", "Failed to transform XML with XSLT", exception);
        } catch (Exception exception) {
            if (isInterrupted(exception)) {
                throw interruptedConversionException(exception);
            }
            if (isBlockedResourceAccess(exception)) {
                throw new ConversionException(
                        "EXTERNAL_RESOURCE_BLOCKED",
                        "External XML/XSLT resources are not allowed",
                        exception);
            }
            throw new ConversionException("TRANSFORMATION_ERROR", "Unexpected XML/XSLT processing error", exception);
        }
    }

    private byte[] renderWithFop(byte[] foBytes, OutputFormat outputFormat, String correlationId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            throwIfInterrupted();
            TransformerFactory transformerFactory = createSecureTransformerFactory();
            FOUserAgent userAgent = fopFactory.newFOUserAgent();
            Fop fop = fopFactory.newFop(outputFormat.rendererMimeType(), userAgent, outputStream);
            Transformer identity = transformerFactory.newTransformer();
            Source source = new StreamSource(new ByteArrayInputStream(foBytes));
            Result result = new SAXResult(fop.getDefaultHandler());
            identity.transform(source, result);
            return outputStream.toByteArray();
        } catch (TransformerException | SAXException exception) {
            if (isInterrupted(exception)) {
                throw interruptedConversionException(exception);
            }
            if (isBlockedResourceAccess(exception)) {
                throw new ConversionException(
                        "EXTERNAL_RESOURCE_BLOCKED",
                        "External FOP resources are not allowed",
                        exception);
            }
            log.error(
                    "Apache FOP render failed for format {} and correlationId {}",
                    outputFormat,
                    correlationId,
                    exception);
            throw new ConversionException("FOP_RENDER_FAILED", "Apache FOP failed to render the document", exception);
        } catch (Exception exception) {
            if (isInterrupted(exception)) {
                throw interruptedConversionException(exception);
            }
            if (isBlockedResourceAccess(exception)) {
                throw new ConversionException(
                        "EXTERNAL_RESOURCE_BLOCKED",
                        "External FOP resources are not allowed",
                        exception);
            }
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

    private TransformerFactory createSecureTransformerFactory() {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException exception) {
            throw new IllegalStateException("Failed to enable secure XML processing", exception);
        }
        setTransformerAttribute(transformerFactory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setTransformerAttribute(transformerFactory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        transformerFactory.setURIResolver(blockingUriResolver());
        return transformerFactory;
    }

    private void setTransformerAttribute(TransformerFactory transformerFactory, String name, String value) {
        try {
            transformerFactory.setAttribute(name, value);
        } catch (IllegalArgumentException exception) {
            log.debug("TransformerFactory does not support attribute {}", name, exception);
        }
    }

    private URIResolver blockingUriResolver() {
        return (href, base) -> {
            throw new TransformerException(BLOCKED_RESOURCE_MESSAGE + ": " + href);
        };
    }

    private static List<Path> trustedFontDirectories(Path basePath) {
        List<Path> directories = new ArrayList<>();
        directories.add(basePath.resolve("fonts"));
        directories.add(Path.of(System.getProperty("java.home"), "lib", "fonts"));

        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("win")) {
            String windowsDirectory = System.getenv("WINDIR");
            if (windowsDirectory != null && !windowsDirectory.isBlank()) {
                directories.add(Path.of(windowsDirectory, "Fonts"));
            }
        } else if (operatingSystem.contains("mac")) {
            directories.add(Path.of("/System/Library/Fonts"));
            directories.add(Path.of("/Library/Fonts"));
        } else {
            directories.add(Path.of("/usr/share/fonts"));
            directories.add(Path.of("/usr/local/share/fonts"));
        }
        return directories;
    }

    static ResourceResolver createSecureResourceResolver(List<Path> allowedFontsDirectories) {
        ResourceResolver delegate = ResourceResolverFactory.createDefaultResourceResolver();
        List<Path> normalizedFontsDirectories = allowedFontsDirectories.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        return new ResourceResolver() {
            @Override
            public org.apache.xmlgraphics.io.Resource getResource(URI uri) throws IOException {
                if (isBlockedFileOrRemoteResource(uri, normalizedFontsDirectories)) {
                    throw new IOException(BLOCKED_RESOURCE_MESSAGE + ": " + uri);
                }
                return delegate.getResource(uri);
            }

            @Override
            public OutputStream getOutputStream(URI uri) throws IOException {
                if (isBlockedFileOrRemoteResource(uri, normalizedFontsDirectories)) {
                    throw new IOException(BLOCKED_RESOURCE_MESSAGE + ": " + uri);
                }
                return delegate.getOutputStream(uri);
            }
        };
    }

    private static boolean isBlockedFileOrRemoteResource(URI uri, List<Path> allowedFontsDirectories) {
        if (uri == null || uri.getScheme() == null) {
            return false;
        }

        String scheme = uri.getScheme().toLowerCase();
        if ("http".equals(scheme) || "https".equals(scheme) || "ftp".equals(scheme) || "jar".equals(scheme)) {
            return true;
        }
        if (!"file".equals(scheme)) {
            return false;
        }

        try {
            Path resourcePath = Path.of(uri).toAbsolutePath().normalize();
            return allowedFontsDirectories.stream().noneMatch(resourcePath::startsWith);
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private boolean isBlockedResourceAccess(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(BLOCKED_RESOURCE_MESSAGE)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw interruptedConversionException(null);
        }
    }

    private ConversionException interruptedConversionException(Exception cause) {
        Thread.currentThread().interrupt();
        return cause == null
                ? new ConversionException("PROCESSING_INTERRUPTED", INTERRUPTED_CONVERSION_MESSAGE)
                : new ConversionException("PROCESSING_INTERRUPTED", INTERRUPTED_CONVERSION_MESSAGE, cause);
    }

    private boolean isInterrupted(Throwable throwable) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}