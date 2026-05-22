package com.example.fopconverter.infrastructure.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("fop")
public class FopHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        return Health.up().withDetail("renderer", "apache-fop").build();
    }
}