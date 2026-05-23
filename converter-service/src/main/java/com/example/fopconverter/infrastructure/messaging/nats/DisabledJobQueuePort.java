package com.example.fopconverter.infrastructure.messaging.nats;

import com.example.fopconverter.application.dto.QueuedConversionJob;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.out.JobQueuePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "converter.nats", name = "enabled", havingValue = "false")
public class DisabledJobQueuePort implements JobQueuePort {

    @Override
    public void publish(QueuedConversionJob job) {
        throw new ConversionException("ASYNC_UNAVAILABLE", "Asynchronous conversion is disabled");
    }
}