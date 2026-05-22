package com.example.fopconverter.infrastructure.messaging.nats;

import com.example.fopconverter.application.port.out.JobQueuePort;
import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class NatsPublisher implements JobQueuePort {
    private final JetStream jetStream;
    private final ObjectMapper objectMapper;
    private final ConversionProperties properties;

    public NatsPublisher(JetStream jetStream, ObjectMapper objectMapper, ConversionProperties properties) {
        this.jetStream = jetStream;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void publish(ConversionJob job) {
        if (!properties.getNats().isEnabled()) {
            return;
        }

        try {
            PublishAck ignored = jetStream.publish(
                    properties.getNats().getRequestsSubject(),
                    objectMapper.writeValueAsString(JobEnvelope.from(job)).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize job message", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to publish job to NATS", exception);
        }
    }
}