package com.example.fopconverter.domain.model;

import java.util.Arrays;
import java.util.Objects;

public final class ConversionDocument {
    private final String fileName;
    private final byte[] content;

    public ConversionDocument(String fileName, byte[] content) {
        this.fileName = Objects.requireNonNullElse(fileName, "document");
        this.content = Objects.requireNonNull(content, "content cannot be null").clone();
    }

    public String fileName() {
        return fileName;
    }

    public byte[] content() {
        return content.clone();
    }

    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConversionDocument that)) {
            return false;
        }
        return Objects.equals(fileName, that.fileName) && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, Arrays.hashCode(content));
    }
}