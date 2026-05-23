package com.example.fopconverter.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fopconverter.domain.model.ConversionDocument;
import com.example.fopconverter.domain.model.ConversionSource;
import com.example.fopconverter.domain.model.InputPayloadType;
import com.example.fopconverter.infrastructure.config.ConversionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FileSystemJobPayloadStoreTest {

    @Test
    void shouldRoundTripQueuedPayloadWithoutEmbeddingItInQueueMessage() throws Exception {
        Path tempDirectory = Files.createTempDirectory("job-payload-store-test");
        ConversionProperties properties = new ConversionProperties();
        properties.getStorage().setArtifactDirectory(tempDirectory.toString());
        FileSystemJobPayloadStore store = new FileSystemJobPayloadStore(properties, new ObjectMapper());
        ConversionSource source = new ConversionSource(
                InputPayloadType.XML_XSLT,
                new ConversionDocument("sample.xml", "<root/>".getBytes(StandardCharsets.UTF_8)),
                new ConversionDocument("sample.xslt", "<xsl:stylesheet/>".getBytes(StandardCharsets.UTF_8)),
                "template-hash");

        String location = store.store(source);
            Path storedPath = tempDirectory.resolve(location).normalize();
        ConversionSource restored = store.read(location);
            String persistedJson = Files.readString(storedPath);

            assertThat(location).startsWith("requests/");
            assertThat(Path.of(location).isAbsolute()).isFalse();
        assertThat(restored).isEqualTo(source);
        assertThat(persistedJson)
                .contains("primaryDocumentBase64")
                .contains("secondaryDocumentBase64")
                .doesNotContain("outputFormat")
                .doesNotContain("jobId");
    }

    @Test
    void shouldDeleteQueuedPayloadFileAfterItIsNoLongerNeeded() throws Exception {
        Path tempDirectory = Files.createTempDirectory("job-payload-store-delete-test");
        ConversionProperties properties = new ConversionProperties();
        properties.getStorage().setArtifactDirectory(tempDirectory.toString());
        FileSystemJobPayloadStore store = new FileSystemJobPayloadStore(properties, new ObjectMapper());
        ConversionSource source = new ConversionSource(
                InputPayloadType.XSL_FO,
                new ConversionDocument("sample.fo", "<root/>".getBytes(StandardCharsets.UTF_8)),
                null,
                "template-hash");

        String location = store.store(source);
        Path storedPath = tempDirectory.resolve(location).normalize();

        store.delete(location);

        assertThat(Files.exists(storedPath)).isFalse();
        }

        @Test
        void shouldRemainCompatibleWithPreviouslyStoredAbsolutePayloadLocation() throws Exception {
        Path tempDirectory = Files.createTempDirectory("job-payload-store-absolute-path-test");
        ConversionProperties properties = new ConversionProperties();
        properties.getStorage().setArtifactDirectory(tempDirectory.toString());
        FileSystemJobPayloadStore store = new FileSystemJobPayloadStore(properties, new ObjectMapper());
        ConversionSource source = new ConversionSource(
            InputPayloadType.XSL_FO,
            new ConversionDocument("sample.fo", "<root/>".getBytes(StandardCharsets.UTF_8)),
            null,
            "template-hash");

        String relativeLocation = store.store(source);
        Path absoluteLocation = tempDirectory.resolve(relativeLocation).normalize();

        ConversionSource restored = store.read(absoluteLocation.toString());

        assertThat(restored).isEqualTo(source);
    }
}