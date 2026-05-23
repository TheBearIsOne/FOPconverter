package com.example.fopconverter.application.port.out;

import com.example.fopconverter.application.dto.QueuedConversionJob;

public interface JobQueuePort {
    void publish(QueuedConversionJob job);
}