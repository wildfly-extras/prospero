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

package com.redhat.prospero.installation;

import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.api.SavedState;
import com.redhat.prospero.model.ManifestYamlSupport;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Test;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LocalInstallationHistoryTest {

    private Path installation;

    @After
    public void tearDown() throws Exception {
        if (Files.exists(installation)) {
            FileUtils.deleteDirectory(installation.toFile());
        }
    }

    @Test
    public void getHistoryOfInstallations() throws Exception {
        final InstallationMetadata metadata = mockInstallation();

        List<SavedState> history = metadata.getRevisions();

        assertEquals(1, history.size());
    }

    @Test
    public void revertToPreviousVersion() throws Exception {
        final InstallationMetadata metadata = mockInstallation();

        metadata.getManifest().updateVersion(new DefaultArtifact("foo:bar:1.1.2"));
        metadata.writeFiles();

        final SavedState previousState = metadata.getRevisions().get(1);

        final InstallationMetadata reverted = metadata.rollback(previousState);
        assertEquals("1.1.1", reverted.getManifest().find(new DefaultArtifact("foo:bar:1.1.0")).getVersion());

    }

    @Test
    public void statusRepresentsAction() throws Exception {
        final InstallationMetadata metadata = mockInstallation();

        metadata.getManifest().updateVersion(new DefaultArtifact("foo:bar:1.1.2"));
        metadata.writeFiles();

        final SavedState previousState = metadata.getRevisions().get(1);

        final InstallationMetadata reverted = metadata.rollback(previousState);

        final List<SavedState> revisions = reverted.getRevisions();
        assertEquals(SavedState.Type.ROLLBACK, revisions.get(0).getType());
        assertEquals(SavedState.Type.UPDATE, revisions.get(1).getType());
        assertEquals(SavedState.Type.INSTALL, revisions.get(2).getType());
    }

    @Test
    public void showDifferences() throws Exception {
        final InstallationMetadata metadata = mockInstallation();

        metadata.getManifest().updateVersion(new DefaultArtifact("foo:bar:1.1.2"));
        metadata.writeFiles();

        final SavedState previousState = metadata.getRevisions().get(1);

        final List<ArtifactChange> changes = metadata.getChangesSince(previousState);
        assertEquals(1, changes.size());
        assertEquals("foo", changes.get(0).getOldVersion().getGroupId());
        assertEquals("bar", changes.get(0).getOldVersion().getArtifactId());
        assertEquals("1.1.1", changes.get(0).getOldVersion().getVersion());
        assertEquals("1.1.2", changes.get(0).getNewVersion().getVersion());
    }

    @Test
    public void showDifferences_noChanges() throws Exception {
        final InstallationMetadata metadata = mockInstallation();

        metadata.getManifest().updateVersion(new DefaultArtifact("foo:bar:1.1.2"));
        metadata.writeFiles();

        final SavedState previousState = metadata.getRevisions().get(0);

        final List<ArtifactChange> changes = metadata.getChangesSince(previousState);
        assertEquals(0, changes.size());
    }

    private InstallationMetadata mockInstallation() throws Exception {
        installation = Files.createTempDirectory("installation");
        installation.toFile().deleteOnExit();
        Files.createDirectories(installation.resolve(InstallationMetadata.METADATA_DIR));
        try (FileWriter fw = new FileWriter(installation.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.CHANNELS_FILE_NAME).toFile())) {
            fw.write("[]");
        }
        final Manifest manifest = new Manifest(Arrays.asList(new DefaultArtifact("foo:bar:1.1.1")), installation.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        ManifestYamlSupport.write(manifest);

        final InstallationMetadata metadata = new InstallationMetadata(installation);
        metadata.writeFiles();
        return metadata;
    }
}
