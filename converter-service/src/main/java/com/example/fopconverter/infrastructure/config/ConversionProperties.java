package com.example.fopconverter.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "converter")
public class ConversionProperties {
    private int maxInputBytes = 10_485_760;
    private Duration syncTimeout = Duration.ofSeconds(60);
    private Duration cacheTtl = Duration.ofHours(6);
    private int maxConcurrentConversions = 8;
    private Storage storage = new Storage();
    private Nats nats = new Nats();
    private Webhook webhook = new Webhook();

    public int getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(int maxInputBytes) {
        this.maxInputBytes = maxInputBytes;
    }

    public Duration getSyncTimeout() {
        return syncTimeout;
    }

    public void setSyncTimeout(Duration syncTimeout) {
        this.syncTimeout = syncTimeout;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getMaxConcurrentConversions() {
        return maxConcurrentConversions;
    }

    public void setMaxConcurrentConversions(int maxConcurrentConversions) {
        this.maxConcurrentConversions = maxConcurrentConversions;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Nats getNats() {
        return nats;
    }

    public void setNats(Nats nats) {
        this.nats = nats;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public void setWebhook(Webhook webhook) {
        this.webhook = webhook;
    }

    public static class Storage {
        private String artifactDirectory = "artifacts";

        public String getArtifactDirectory() {
            return artifactDirectory;
        }

        public void setArtifactDirectory(String artifactDirectory) {
            this.artifactDirectory = artifactDirectory;
        }
    }

    public static class Nats {
        private String streamName = "converter-jobs";
        private String requestsSubject = "converter.jobs.requests";
        private String updatesSubject = "converter.jobs.updates";
        private String durableConsumer = "converter-worker";
        private boolean enabled = true;

        public String getStreamName() {
            return streamName;
        }

        public void setStreamName(String streamName) {
            this.streamName = streamName;
        }

        public String getRequestsSubject() {
            return requestsSubject;
        }

        public void setRequestsSubject(String requestsSubject) {
            this.requestsSubject = requestsSubject;
        }

        public String getUpdatesSubject() {
            return updatesSubject;
        }

        public void setUpdatesSubject(String updatesSubject) {
            this.updatesSubject = updatesSubject;
        }

        public String getDurableConsumer() {
            return durableConsumer;
        }

        public void setDurableConsumer(String durableConsumer) {
            this.durableConsumer = durableConsumer;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Webhook {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}