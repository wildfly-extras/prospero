/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package integration;

import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.cli.actions.GalleonProvision;
import com.redhat.prospero.cli.actions.InstallationHistory;
import com.redhat.prospero.api.SavedState;
import com.redhat.prospero.cli.actions.Update;
import com.redhat.prospero.model.ManifestXmlSupport;
import com.redhat.prospero.model.XmlException;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class InstallationHistoryTest {

    private static final String OUTPUT_DIR = "target/server";
    private static final Path OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();

    @Before
    public void setUp() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
    }

    @Test
    public void listUpdates() throws Exception {
        // installCore
        final Path channelFile = TestUtil.prepareChannelFile("local-repo-desc.json");
        new GalleonProvision().installFeaturePack("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final", OUTPUT_PATH.toString(), channelFile.toString());

        // updateCore
        TestUtil.prepareChannelFile(OUTPUT_PATH.resolve(InstallationMetadata.CHANNELS_FILE_NAME), "local-repo-desc.json", "local-updates-repo-desc.json");
        new Update(OUTPUT_PATH, true).doUpdateAll();

        // get history
        List<SavedState> states = new InstallationHistory().getRevisions(OUTPUT_PATH);

        // assert two entries
        assertEquals(2, states.size());
    }

    @Test
    public void rollbackChanges() throws Exception {
        final Path channelFile = TestUtil.prepareChannelFile("local-repo-desc.json");
        new GalleonProvision().installFeaturePack("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final", OUTPUT_PATH.toString(), channelFile.toString());

        TestUtil.prepareChannelFile(OUTPUT_PATH.resolve(InstallationMetadata.CHANNELS_FILE_NAME), "local-repo-desc.json", "local-updates-repo-desc.json");
        new Update(OUTPUT_PATH, true).doUpdateAll();

        final InstallationHistory installationHistory = new InstallationHistory();
        final List<SavedState> revisions = installationHistory.getRevisions(OUTPUT_PATH);

        final SavedState savedState = revisions.get(1);
        installationHistory.rollback(OUTPUT_PATH, savedState);

        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.0.Final", wildflyCliArtifact.get().getVersion());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws XmlException {
        final File manifestFile = OUTPUT_PATH.resolve(InstallationMetadata.MANIFEST_FILE_NAME).toFile();
        return ManifestXmlSupport.parse(manifestFile).getArtifacts().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst();
    }
}
