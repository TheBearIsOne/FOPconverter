package com.example.fopconverter.domain.model;

import java.util.Arrays;

public enum OutputFormat {
    PDF("application/pdf", "pdf", "application/pdf"),
    POSTSCRIPT("application/postscript", "ps", "application/postscript"),
    PCL("application/x-pcl", "pcl", "application/x-pcl"),
    AFP("application/x-afp", "afp", "application/x-afp"),
    IOCA("image/x-afp+ioca", "ioca", "image/x-afp+ioca"),
    RTF("application/rtf", "rtf", "application/rtf"),
    XML("application/xml", "xml", "application/xml"),
    TXT("text/plain", "txt", "text/plain");

    private final String mimeType;
    private final String fileExtension;
    private final String rendererMimeType;

    OutputFormat(String mimeType, String fileExtension, String rendererMimeType) {
        this.mimeType = mimeType;
        this.fileExtension = fileExtension;
        this.rendererMimeType = rendererMimeType;
    }

    public String mimeType() {
        return mimeType;
    }

    public String fileExtension() {
        return fileExtension;
    }

    public String rendererMimeType() {
        return rendererMimeType;
    }

    public static OutputFormat from(String value) {
        return Arrays.stream(values())
                .filter(format -> format.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported output format: " + value));
    }
}