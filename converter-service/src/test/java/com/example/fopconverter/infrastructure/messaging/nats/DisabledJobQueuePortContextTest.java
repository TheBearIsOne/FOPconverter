package com.example.fopconverter.infrastructure.messaging.nats;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fopconverter.application.port.out.JobQueuePort;
import com.example.fopconverter.infrastructure.config.NatsConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

class DisabledJobQueuePortContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("converter.nats.enabled=false");

    @Test
    void shouldProvideFallbackQueuePortWithoutCreatingNatsConnection() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JobQueuePort.class);
            assertThat(context).hasSingleBean(DisabledJobQueuePort.class);
            assertThat(context).doesNotHaveBean("natsConnection");
        });
    }

    @Import({DisabledJobQueuePort.class, NatsConfiguration.class})
    static class TestConfiguration {}
}