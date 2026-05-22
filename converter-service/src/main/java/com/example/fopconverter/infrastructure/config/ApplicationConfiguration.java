package com.example.fopconverter.infrastructure.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConversionProperties.class)
public class ApplicationConfiguration {

    @Bean(destroyMethod = "close")
    ExecutorService conversionExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}