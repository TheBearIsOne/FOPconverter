package com.example.fopconverter.infrastructure.storage;

import com.example.fopconverter.application.port.out.ArtifactStoragePort;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FileSystemArtifactStorage implements ArtifactStoragePort {
    private final Path rootDirectory;

    public FileSystemArtifactStorage(ConversionProperties properties) {
        this.rootDirectory = Path.of(properties.getStorage().getArtifactDirectory()).toAbsolutePath();
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize artifact directory", exception);
        }
    }

    @Override
    public String store(String fileName, byte[] bytes) {
        Path target = rootDirectory.resolve(UUID.randomUUID() + "-" + fileName).normalize();
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            return target.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store artifact", exception);
        }
    }

    @Override
    public byte[] read(String location) {
        try {
            return Files.readAllBytes(Path.of(location));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read artifact", exception);
        }
    }
}