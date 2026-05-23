package com.example.fopconverter.infrastructure.messaging.nats;

import com.example.fopconverter.application.port.in.ProcessQueuedConversionUseCase;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import java.nio.file.NoSuchFileException;
import java.nio.file.InvalidPathException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("async-worker")
@ConditionalOnProperty(prefix = "converter.nats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NatsConsumer {
    private static final Logger log = LoggerFactory.getLogger(NatsConsumer.class);

    private final Dispatcher dispatcher;

    public NatsConsumer(
            Connection connection,
            JetStream jetStream,
            JetStreamManagement jetStreamManagement,
            ObjectMapper objectMapper,
            ConversionProperties properties,
            ProcessQueuedConversionUseCase processQueuedConversionUseCase) {
        this.dispatcher = connection.createDispatcher(message -> {});
        String streamName = properties.getNats().getStreamName();
        String requestsSubject = properties.getNats().getRequestsSubject();
        String durableConsumer = properties.getNats().getDurableConsumer();
        try {
            subscribe(
                    jetStream,
                    objectMapper,
                    processQueuedConversionUseCase,
                    requestsSubject,
                    streamName,
                    durableConsumer);
        } catch (Exception exception) {
            if (isExistingConsumerConflict(exception)) {
                recreateIncompatibleConsumer(jetStreamManagement, streamName, durableConsumer, exception);
                try {
                    subscribe(
                            jetStream,
                            objectMapper,
                            processQueuedConversionUseCase,
                            requestsSubject,
                            streamName,
                            durableConsumer);
                    return;
                } catch (Exception retryException) {
                    throw new IllegalStateException("Failed to resubscribe to NATS JetStream after recreating consumer", retryException);
                }
            }
            throw new IllegalStateException("Failed to subscribe to NATS JetStream", exception);
        }
    }

    private void subscribe(
            JetStream jetStream,
            ObjectMapper objectMapper,
            ProcessQueuedConversionUseCase processQueuedConversionUseCase,
            String requestsSubject,
            String streamName,
            String durableConsumer)
            throws Exception {
        jetStream.subscribe(
                requestsSubject,
                durableConsumer,
                dispatcher,
                message -> handleMessage(message, objectMapper, processQueuedConversionUseCase),
                false,
                PushSubscribeOptions.builder()
                        .stream(streamName)
                        .durable(durableConsumer)
                        .deliverGroup(durableConsumer)
                        .build());
    }

    static void handleMessage(
            Message message,
            ObjectMapper objectMapper,
            ProcessQueuedConversionUseCase processQueuedConversionUseCase) {
        try {
            JobEnvelope envelope =
                    objectMapper.readValue(new String(message.getData(), StandardCharsets.UTF_8), JobEnvelope.class);
            processQueuedConversionUseCase.process(envelope.toQueuedJob());
            message.ack();
        } catch (Exception exception) {
            log.error("Failed to process conversion job message from NATS", exception);
            if (isPoisonMessage(exception)) {
                message.term();
                return;
            }
            message.nak();
        }
    }

    private static boolean isPoisonMessage(Exception exception) {
        Throwable rootCause = rootCauseOf(exception);
        return rootCause instanceof JsonProcessingException
                || rootCause instanceof IllegalArgumentException
                || rootCause instanceof InvalidPathException
                || rootCause instanceof NoSuchFileException;
    }

    private static boolean isExistingConsumerConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IllegalArgumentException
                    && current.getMessage() != null
                    && current.getMessage().contains("Existing consumer cannot be modified")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void recreateIncompatibleConsumer(
            JetStreamManagement jetStreamManagement,
            String streamName,
            String durableConsumer,
            Exception subscribeException) {
        log.warn(
                "Deleting incompatible NATS consumer stream={} durable={} and retrying subscription",
                streamName,
                durableConsumer,
                subscribeException);
        try {
            jetStreamManagement.deleteConsumer(streamName, durableConsumer);
        } catch (Exception deleteException) {
            subscribeException.addSuppressed(deleteException);
            throw new IllegalStateException("Failed to delete incompatible NATS consumer", subscribeException);
        }
    }

    private static Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}