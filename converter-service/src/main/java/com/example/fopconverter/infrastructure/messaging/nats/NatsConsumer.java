package com.example.fopconverter.infrastructure.messaging.nats;

import com.example.fopconverter.application.port.out.ArtifactStoragePort;
import com.example.fopconverter.application.port.out.ConversionEnginePort;
import com.example.fopconverter.application.port.out.JobStatusStorePort;
import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("async-worker")
public class NatsConsumer {
    private static final Logger log = LoggerFactory.getLogger(NatsConsumer.class);

    private final Dispatcher dispatcher;

    public NatsConsumer(
            Connection connection,
            JetStream jetStream,
            ObjectMapper objectMapper,
            ConversionProperties properties,
            ConversionEnginePort conversionEnginePort,
            ArtifactStoragePort artifactStoragePort,
            JobStatusStorePort jobStatusStorePort) {
        this.dispatcher = connection.createDispatcher(message -> {});
        String durableConsumer = properties.getNats().getDurableConsumer();
        try {
            jetStream.subscribe(
                    properties.getNats().getRequestsSubject(),
                    durableConsumer,
                    dispatcher,
                    message -> handle(
                            message, objectMapper, conversionEnginePort, artifactStoragePort, jobStatusStorePort),
                    false,
                    PushSubscribeOptions.builder()
                            .stream(properties.getNats().getStreamName())
                            .durable(durableConsumer)
                            .deliverGroup(durableConsumer)
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to subscribe to NATS JetStream", exception);
        }
    }

    private void handle(
            Message message,
            ObjectMapper objectMapper,
            ConversionEnginePort conversionEnginePort,
            ArtifactStoragePort artifactStoragePort,
            JobStatusStorePort jobStatusStorePort) {
        try {
            JobEnvelope envelope =
                    objectMapper.readValue(new String(message.getData(), StandardCharsets.UTF_8), JobEnvelope.class);
            ConversionJob job = envelope.toDomain();
            job.markProcessing();
            jobStatusStorePort.save(job);
            var result = conversionEnginePort.convert(job.source(), job.outputFormat(), job.correlationId());
            String location = artifactStoragePort.store(result.fileName(), result.bytes());
            job.markCompleted(location);
            jobStatusStorePort.save(job);
            message.ack();
        } catch (Exception exception) {
            log.error("Failed to process conversion job message from NATS", exception);
            message.nak();
        }
    }
}