package com.example.fopconverter.infrastructure.messaging.nats;

import com.example.fopconverter.application.dto.QueuedConversionJob;
import com.example.fopconverter.application.exception.ConversionException;
import com.example.fopconverter.application.port.out.JobQueuePort;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "converter.nats", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    public void publish(QueuedConversionJob job) {
        try {
            jetStream.publish(
                    properties.getNats().getRequestsSubject(),
                    objectMapper.writeValueAsString(JobEnvelope.from(job)).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException exception) {
            throw new ConversionException("JOB_SUBMISSION_FAILED", "Failed to serialize async conversion job", exception);
        } catch (Exception exception) {
            throw new ConversionException("JOB_SUBMISSION_FAILED", "Failed to publish job to async queue", exception);
        }
    }
}