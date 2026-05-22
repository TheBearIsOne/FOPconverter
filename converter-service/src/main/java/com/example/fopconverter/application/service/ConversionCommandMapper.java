package com.example.fopconverter.application.service;

import com.example.fopconverter.application.dto.ConversionCommand;
import com.example.fopconverter.domain.model.ConversionSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public class ConversionCommandMapper {

    public ConversionSource toSource(ConversionCommand command) {
        String templateHash = command.secondaryDocument() == null
                ? DigestUtils.sha256Hex(command.primaryDocument().content())
                : DigestUtils.sha256Hex(command.secondaryDocument().content());
        return new ConversionSource(
                command.payloadType(),
                command.primaryDocument(),
                command.secondaryDocument(),
                templateHash);
    }
}