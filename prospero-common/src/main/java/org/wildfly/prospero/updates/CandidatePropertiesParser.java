package org.wildfly.prospero.updates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.wildfly.prospero.api.exceptions.MetadataException;

import java.io.IOException;
import java.nio.file.Path;


public class CandidatePropertiesParser {

    protected static final YAMLFactory YAML_FACTORY = new YAMLFactory();
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(YAML_FACTORY);

    public static CandidateProperties read(Path file) throws IOException, MetadataException {
        final JsonNode node = OBJECT_MAPPER.readTree(file.toFile());
        checkSchemaVersion(node);

        return OBJECT_MAPPER.readValue(file.toFile(), CandidateProperties.class);
    }

    private static void checkSchemaVersion(JsonNode node) throws MetadataException {
        JsonNode schemaVersion = node.path("schemaVersion");
        String version = schemaVersion.asText();
        if (version == null || version.isEmpty()) {
            throw new MetadataException("The candidate properties file does not have schemaVersion field.");
        }
        if (!version.equals("1.0.0")) {
            throw new MetadataException("Unknown schemaVersion for the candidate properties file.");
        }
    }

    public static void write(CandidateProperties properties, Path file) throws IOException {
        OBJECT_MAPPER.writeValue(file.toFile(), properties);
    }
}
