package com.example.fopconverter.infrastructure.config;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(prefix = "converter.nats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NatsConfiguration {
    @Bean(destroyMethod = "close")
    Connection natsConnection(@Value("${spring.nats.url:nats://localhost:4222}") String natsUrl)
            throws IOException, InterruptedException {
        Options options = new Options.Builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofSeconds(5))
                .build();
        return Nats.connect(options);
    }

    @Bean
    JetStream jetStream(Connection connection, JetStreamManagement management, ConversionProperties properties)
            throws Exception {
        ensureStream(management, properties);
        return connection.jetStream();
    }

    @Bean
    JetStreamManagement jetStreamManagement(Connection connection) throws IOException {
        return connection.jetStreamManagement();
    }

    private void ensureStream(JetStreamManagement management, ConversionProperties properties) throws Exception {
        try {
            management.getStreamInfo(properties.getNats().getStreamName());
        } catch (Exception ignored) {
            StreamConfiguration configuration = StreamConfiguration.builder()
                    .name(properties.getNats().getStreamName())
                    .storageType(StorageType.File)
                .subjects(
                                    properties.getNats().getRequestsSubject(),
                                    properties.getNats().getUpdatesSubject())
                    .build();
            management.addStream(configuration);
        }
    }
}