package com.example.fopconverter.application.port.out;

import com.example.fopconverter.domain.model.ConversionSource;

public interface JobPayloadStorePort {
    String store(ConversionSource source);

    ConversionSource read(String location);

    void delete(String location);
}