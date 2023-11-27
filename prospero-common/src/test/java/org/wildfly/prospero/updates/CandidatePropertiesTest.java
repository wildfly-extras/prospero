package org.wildfly.prospero.updates;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.api.exceptions.MetadataException;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CandidatePropertiesTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void serializeProperties() throws Exception {
        final CandidateProperties candidateProperties = new CandidateProperties(
                List.of(new CandidateProperties.ComponentUpdate("foo", "bar", "test")));

        final File resultFile = temp.newFile();
        CandidatePropertiesParser.write(candidateProperties, resultFile.toPath());

        final CandidateProperties readProperties = CandidatePropertiesParser.read(resultFile.toPath());

        assertEquals(candidateProperties, readProperties);
    }

    @Test
    public void readPropertiesWithEmptyUpdateList() throws Exception {
        final File resultFile = temp.newFile();
        Files.writeString(resultFile.toPath(), "schemaVersion: \"1.0.0\"\n" +
                "updates:");

        final CandidateProperties readProperties = CandidatePropertiesParser.read(resultFile.toPath());

        assertEquals(Collections.emptyList(), readProperties.getUpdates());
    }

    @Test
    public void readPropertiesWithoutVersion() throws Exception {
        final File resultFile = temp.newFile();
        Files.writeString(resultFile.toPath(), "updates:");

        Assertions.assertThatThrownBy(()->CandidatePropertiesParser.read(resultFile.toPath()))
                .isInstanceOf(MetadataException.class);
    }
}