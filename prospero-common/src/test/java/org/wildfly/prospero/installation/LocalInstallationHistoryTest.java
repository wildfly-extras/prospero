/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.installation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.test.MetadataTestUtils;

import static org.junit.Assert.assertEquals;

public class LocalInstallationHistoryTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path installation;

    @Before
    public void setUp() throws IOException {
        this.installation = tempDir.newFolder().toPath();
    }

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

        updateManifest(metadata);

        final SavedState previousState = metadata.getRevisions().get(1);

        final InstallationMetadata reverted = metadata.getSavedState(previousState);
        assertEquals("1.1.1", reverted.find(new DefaultArtifact("foo:bar:1.1.0")).getVersion());

    }

    @Test
    public void getSaveStateDoesntChangeCurrentState() throws Exception {
        final InstallationMetadata metadata = mockInstallation();

        updateManifest(metadata);

        final SavedState previousState = metadata.getRevisions().get(1);

        metadata.getSavedState(previousState);

        final List<SavedState> revisions = metadata.getRevisions();
        assertEquals(SavedState.Type.UPDATE, revisions.get(0).getType());
        assertEquals(SavedState.Type.INSTALL, revisions.get(1).getType());
        assertEquals("1.1.2", metadata.find(new DefaultArtifact("foo:bar:1.1.0")).getVersion());
    }

    @Test
    public void showDifferences() throws Exception {
        final InstallationMetadata metadata = mockInstallation();

        updateManifest(metadata);

        final SavedState previousState = metadata.getRevisions().get(1);

        final List<ArtifactChange> changes = metadata.getChangesSince(previousState).getArtifactChanges();
        assertEquals(1, changes.size());
        assertEquals("foo:bar", changes.get(0).getArtifactName());
        assertEquals("1.1.1", changes.get(0).getOldVersion().get());
        assertEquals("1.1.2", changes.get(0).getNewVersion().get());
    }

    @Test
    public void showDifferences_noChanges() throws Exception {
        final InstallationMetadata metadata = mockInstallation();

        updateManifest(metadata);

        final SavedState previousState = metadata.getRevisions().get(0);

        final List<ArtifactChange> changes = metadata.getChangesSince(previousState).getArtifactChanges();
        assertEquals(0, changes.size());
    }

    private void updateManifest(InstallationMetadata metadata) throws MetadataException {
        final ChannelManifest manifest = new ChannelManifest("test", "test-id", "",
                Arrays.asList(new Stream("foo", "bar", "1.1.2", null)));
        metadata.setManifest(manifest);
        metadata.recordProvision(true);
    }

    private InstallationMetadata mockInstallation() throws Exception {
        final ChannelManifest manifest = MetadataTestUtils.createManifest(Arrays.asList(new Stream("foo", "bar", "1.1.1", null)));
        InstallationMetadata installationMetadata = MetadataTestUtils.createInstallationMetadata(installation, manifest,
                List.of(new Channel("test", "", null,
                        List.of(new Repository("test", "file://test.org/test")),
                        new ChannelManifestCoordinate("org.test", "manifest"),
                        null, null)));
        return installationMetadata;
    }
}
