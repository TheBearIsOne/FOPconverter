package com.example.fopconverter.application.port.in;

import com.example.fopconverter.application.dto.QueuedConversionJob;

public interface ProcessQueuedConversionUseCase {
    void process(QueuedConversionJob job);
}