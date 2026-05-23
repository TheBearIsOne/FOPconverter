package com.example.fopconverter.domain.model;

import java.util.Objects;
import java.util.Optional;

public record ConversionSource(
        InputPayloadType payloadType,
        ConversionDocument primaryDocument,
        ConversionDocument secondaryDocument,
        String templateHash) {

    public ConversionSource {
        Objects.requireNonNull(payloadType, "payloadType cannot be null");
        Objects.requireNonNull(primaryDocument, "primaryDocument cannot be null");
        Objects.requireNonNull(templateHash, "templateHash cannot be null");

        if (primaryDocument.isEmpty()) {
            throw new IllegalArgumentException("Primary document cannot be empty");
        }

        switch (payloadType) {
            case XML_XSLT -> {
                if (secondaryDocument == null || secondaryDocument.isEmpty()) {
                    throw new IllegalArgumentException("Secondary XSLT document is required for XML_XSLT payloads");
                }
            }
            case XSL_FO -> {
                if (secondaryDocument != null) {
                    throw new IllegalArgumentException("Secondary document is not supported for XSL_FO payloads");
                }
            }
        }
    }

    public Optional<ConversionDocument> optionalSecondaryDocument() {
        return Optional.ofNullable(secondaryDocument);
    }
}