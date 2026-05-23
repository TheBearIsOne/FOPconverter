package com.example.fopconverter.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class AsyncWorkerProfileConfigurationTest {

    @Test
    void shouldDisableServletStackForAsyncWorkerProfile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-async-worker.yml"));

        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertThat(properties.containsKey("server.port")).isFalse();
        assertThat(properties.getProperty("converter.nats.enabled")).isEqualTo("true");
    }
}