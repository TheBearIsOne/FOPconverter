package com.example.fopconverter.application.port.out;

import com.example.fopconverter.domain.model.ConversionJob;

public interface JobQueuePort {
    void publish(ConversionJob job);
}