package com.example.fopconverter.application.port.in;

import com.example.fopconverter.application.dto.ConversionCommand;
import com.example.fopconverter.application.dto.SubmitConversionResponse;

public interface SubmitConversionUseCase {
    SubmitConversionResponse submit(ConversionCommand command);
}