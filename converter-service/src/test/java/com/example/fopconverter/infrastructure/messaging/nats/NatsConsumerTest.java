package com.example.fopconverter.infrastructure.messaging.nats;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fopconverter.application.dto.QueuedConversionJob;
import com.example.fopconverter.application.port.in.ProcessQueuedConversionUseCase;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.nats.client.impl.AckType;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsJetStreamMetaData;
import io.nats.client.support.Status;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NatsConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldAckSuccessfullyProcessedMessage() throws Exception {
        TestMessage message = messageWith(validEnvelopeJson());
        AtomicBoolean processed = new AtomicBoolean();
        ProcessQueuedConversionUseCase useCase = job -> processed.set(true);

        NatsConsumer.handleMessage(message, objectMapper, useCase);

        assertThat(message.lastSignal).isEqualTo(AckSignal.ACK);
        assertThat(processed.get()).isTrue();
    }

    @Test
    void shouldTermPoisonMessageInsteadOfRetryingItForever() {
        TestMessage message = messageWith("{not-json".getBytes(StandardCharsets.UTF_8));
        ProcessQueuedConversionUseCase useCase = job -> {};

        NatsConsumer.handleMessage(message, objectMapper, useCase);

        assertThat(message.lastSignal).isEqualTo(AckSignal.TERM);
    }

    @Test
    void shouldNakTransientProcessingFailure() throws Exception {
        TestMessage message = messageWith(validEnvelopeJson());
        ProcessQueuedConversionUseCase useCase = job -> {
            throw new IllegalStateException("Redis unavailable", new SocketTimeoutException("timed out"));
        };

        NatsConsumer.handleMessage(message, objectMapper, useCase);

        assertThat(message.lastSignal).isEqualTo(AckSignal.NAK);
    }

    @Test
    void shouldRecreateIncompatibleDurableConsumerAndRetrySubscription() {
        AtomicInteger subscribeAttempts = new AtomicInteger();
        AtomicInteger deleteAttempts = new AtomicInteger();
        Dispatcher dispatcher = proxy(Dispatcher.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
        Connection connection = proxy(Connection.class, (proxy, method, args) -> {
            if (method.getName().equals("createDispatcher")) {
                return dispatcher;
            }
            return defaultValue(method.getReturnType());
        });
        JetStream jetStream = proxy(JetStream.class, (proxy, method, args) -> {
            if (method.getName().equals("subscribe")) {
                if (subscribeAttempts.getAndIncrement() == 0) {
                    throw new IllegalArgumentException(
                            "[SUB-90016] Existing consumer cannot be modified. Changed fields: [deliverGroup]");
                }
                return null;
            }
            return defaultValue(method.getReturnType());
        });
        JetStreamManagement jetStreamManagement = proxy(JetStreamManagement.class, (proxy, method, args) -> {
            if (method.getName().equals("deleteConsumer")) {
                deleteAttempts.incrementAndGet();
                return true;
            }
            return defaultValue(method.getReturnType());
        });
        ConversionProperties properties = new ConversionProperties();

        new NatsConsumer(connection, jetStream, jetStreamManagement, objectMapper, properties, job -> {});

        assertThat(subscribeAttempts.get()).isEqualTo(2);
        assertThat(deleteAttempts.get()).isEqualTo(1);
    }

    private TestMessage messageWith(byte[] payload) {
        return new TestMessage(payload);
    }

    private byte[] validEnvelopeJson() throws Exception {
        JobEnvelope envelope = JobEnvelope.from(new QueuedConversionJob(
                UUID.randomUUID(),
                com.example.fopconverter.domain.model.OutputFormat.PDF,
                Instant.parse("2026-05-24T00:00:00Z"),
                "corr-789",
                "requests/job.json"));
        return objectMapper.writeValueAsBytes(envelope);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private enum AckSignal {
        ACK,
        NAK,
        TERM,
        IN_PROGRESS
    }

    private static final class TestMessage implements Message {
        private final byte[] payload;
        private AckSignal lastSignal;

        private TestMessage(byte[] payload) {
            this.payload = payload;
        }

        @Override
        public String getSubject() {
            return null;
        }

        @Override
        public String getReplyTo() {
            return null;
        }

        @Override
        public boolean hasHeaders() {
            return false;
        }

        @Override
        public Headers getHeaders() {
            return null;
        }

        @Override
        public boolean isStatusMessage() {
            return false;
        }

        @Override
        public Status getStatus() {
            return null;
        }

        @Override
        public byte[] getData() {
            return payload;
        }

        @Override
        public boolean isUtf8mode() {
            return true;
        }

        @Override
        public Subscription getSubscription() {
            return null;
        }

        @Override
        public String getSID() {
            return null;
        }

        @Override
        public Connection getConnection() {
            return null;
        }

        @Override
        public NatsJetStreamMetaData metaData() {
            return null;
        }

        @Override
        public AckType lastAck() {
            return null;
        }

        @Override
        public void ack() {
            lastSignal = AckSignal.ACK;
        }

        @Override
        public void ackSync(Duration timeout) {
            lastSignal = AckSignal.ACK;
        }

        @Override
        public void nak() {
            lastSignal = AckSignal.NAK;
        }

        @Override
        public void nakWithDelay(Duration nakDelay) {
            lastSignal = AckSignal.NAK;
        }

        @Override
        public void nakWithDelay(long nakDelayMillis) {
            lastSignal = AckSignal.NAK;
        }

        @Override
        public void term() {
            lastSignal = AckSignal.TERM;
        }

        @Override
        public void inProgress() {
            lastSignal = AckSignal.IN_PROGRESS;
        }

        @Override
        public boolean isJetStream() {
            return true;
        }
    }
}