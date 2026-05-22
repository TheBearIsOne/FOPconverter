package com.example.fopconverter.application.port.in;

import com.example.fopconverter.application.dto.ConversionCommand;
import com.example.fopconverter.application.dto.SyncConversionResponse;

public interface ConvertSynchronouslyUseCase {
    SyncConversionResponse convert(ConversionCommand command);
}