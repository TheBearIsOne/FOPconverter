package com.example.fopconverter.domain.model;

import java.util.Optional;

public record ConversionSource(
        InputPayloadType payloadType,
        ConversionDocument primaryDocument,
        ConversionDocument secondaryDocument,
        String templateHash) {

    public Optional<ConversionDocument> optionalSecondaryDocument() {
        return Optional.ofNullable(secondaryDocument);
    }
}