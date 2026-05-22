package com.example.fopconverter.application.port.out;

public interface ArtifactStoragePort {
    String store(String fileName, byte[] bytes);

    byte[] read(String location);
}