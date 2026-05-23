package com.example.fopconverter.infrastructure.storage;

import com.example.fopconverter.application.port.out.JobPayloadStorePort;
import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FileSystemJobPayloadStore implements JobPayloadStorePort {
    private final Path storageRootDirectory;
    private final Path payloadRootDirectory;
    private final ObjectMapper objectMapper;

    public FileSystemJobPayloadStore(
            com.example.fopconverter.infrastructure.config.ConversionProperties properties, ObjectMapper objectMapper) {
        this.storageRootDirectory = Path.of(properties.getStorage().getArtifactDirectory()).toAbsolutePath().normalize();
        this.payloadRootDirectory = storageRootDirectory.resolve("requests").normalize();
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(payloadRootDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize job payload directory", exception);
        }
    }

    @Override
    public String store(ConversionSource source) {
        Path target = payloadRootDirectory.resolve(UUID.randomUUID() + ".json").normalize();
        try {
            Files.writeString(
                    target,
                    objectMapper.writeValueAsString(StoredConversionSource.from(source)),
                    StandardOpenOption.CREATE_NEW);
            return storageRootDirectory.relativize(target).toString().replace('\\', '/');
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize queued conversion payload", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store queued conversion payload", exception);
        }
    }

    @Override
    public ConversionSource read(String location) {
        try {
            StoredConversionSource storedSource =
                    objectMapper.readValue(Files.readString(resolveStoredPath(location)), StoredConversionSource.class);
            return storedSource.toDomain();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read queued conversion payload", exception);
        }
    }

    @Override
    public void delete(String location) {
        try {
            Files.deleteIfExists(resolveStoredPath(location));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete queued conversion payload", exception);
        }
    }

    private Path resolveStoredPath(String location) {
        Path rawPath = Path.of(location.replace('\\', '/')).normalize();
        Path resolved = rawPath.isAbsolute()
                ? rawPath.toAbsolutePath().normalize()
                : storageRootDirectory.resolve(rawPath).normalize();
        if (!resolved.startsWith(payloadRootDirectory)) {
            throw new IllegalArgumentException("Queued conversion payload path is outside of the configured storage root");
        }
        return resolved;
    }

    private record StoredConversionSource(
            String payloadType,
            String primaryFileName,
            String primaryDocumentBase64,
            String secondaryFileName,
            String secondaryDocumentBase64,
            String templateHash) {

        static StoredConversionSource from(ConversionSource source) {
            return new StoredConversionSource(
                    source.payloadType().name(),
                    source.primaryDocument().fileName(),
                    Base64.getEncoder().encodeToString(source.primaryDocument().content()),
                    source.optionalSecondaryDocument().map(ConversionDocument::fileName).orElse(null),
                    source.optionalSecondaryDocument()
                            .map(document -> Base64.getEncoder().encodeToString(document.content()))
                            .orElse(null),
                    source.templateHash());
        }

        ConversionSource toDomain() {
            return new ConversionSource(
                    InputPayloadType.valueOf(payloadType),
                    new ConversionDocument(primaryFileName, Base64.getDecoder().decode(primaryDocumentBase64)),
                    secondaryDocumentBase64 == null
                            ? null
                            : new ConversionDocument(secondaryFileName, Base64.getDecoder().decode(secondaryDocumentBase64)),
                    templateHash);
        }
    }
}