package com.example.fopconverter.infrastructure.health;

import io.nats.client.Connection;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("nats")
public class NatsHealthIndicator implements HealthIndicator {
    private final Connection connection;

    public NatsHealthIndicator(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Health health() {
        return connection.getStatus() == Connection.Status.CONNECTED
                ? Health.up().withDetail("status", connection.getStatus().name()).build()
                : Health.down().withDetail("status", connection.getStatus().name()).build();
    }
}