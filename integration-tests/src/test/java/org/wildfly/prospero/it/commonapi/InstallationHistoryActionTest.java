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

package org.wildfly.prospero.it.commonapi;

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.ProvisioningException;
import org.junit.Assert;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Test;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InstallationHistoryActionTest extends WfCoreTestBase {

    private Path channelsFile;

    @After
    public void tearDown() throws Exception {
        if (Files.exists(channelsFile)) {
            Files.delete(channelsFile);
        }
    }

    @Test
    public void listUpdates() throws Exception {
        // installCore
        channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        // updateCore
        MetadataTestUtils.prepareChannel(outputPath.resolve(MetadataTestUtils.INSTALLER_CHANNELS_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        updateAction().performUpdate();

        // get history
        List<SavedState> states = new InstallationHistoryAction(outputPath, new AcceptingConsole()).getRevisions();

        // assert two entries
        assertEquals(2, states.size());
    }

    @Test
    public void rollbackChanges() throws Exception {
        channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);
        final Path modulesPaths = outputPath.resolve(Paths.get("modules", "system", "layers", "base"));
        final Path wildflyCliModulePath = modulesPaths.resolve(Paths.get("org", "jboss", "as", "cli", "main"));

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.prepareChannel(outputPath.resolve(MetadataTestUtils.INSTALLER_CHANNELS_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        updateAction().performUpdate();
        Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Updated jar should be present in module", wildflyCliModulePath.resolve(UPGRADE_JAR).toFile().exists());

        final InstallationHistoryAction historyAction = new InstallationHistoryAction(outputPath, new AcceptingConsole());
        final List<SavedState> revisions = historyAction.getRevisions();

        final SavedState savedState = revisions.get(1);
        historyAction.rollback(savedState, mavenOptions, Collections.emptyList());

        wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Reverted jar should be present in module", wildflyCliModulePath.resolve(BASE_JAR).toFile().exists());

        // assert we only have INSTALL,UPDATE and ROLLBACK states
        assertThat(historyAction.getRevisions())
                .map(SavedState::getType)
                .contains(SavedState.Type.ROLLBACK, SavedState.Type.UPDATE, SavedState.Type.INSTALL);
    }

    @Test
    public void prepareRevertDoesntChangeSourceServer() throws Exception {
        channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);
        final Path modulesPaths = outputPath.resolve(Paths.get("modules", "system", "layers", "base"));
        final Path wildflyCliModulePath = modulesPaths.resolve(Paths.get("org", "jboss", "as", "cli", "main"));
        final Path candidate = temp.newFolder().toPath();

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.prepareChannel(outputPath.resolve(MetadataTestUtils.INSTALLER_CHANNELS_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        updateAction().performUpdate();
        Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Updated jar should be present in module", wildflyCliModulePath.resolve(UPGRADE_JAR).toFile().exists());

        Optional<ManifestVersionRecord> manifestVersionRecord = ManifestVersionRecord.read(
                outputPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(ProsperoMetadataUtils.CURRENT_VERSION_FILE));
        assertTrue("Manifest version record should be present", manifestVersionRecord.isPresent());
        assertEquals("Manifest version record should contain base and update channels",
                2, manifestVersionRecord.get().getUrlManifests().size());

        final InstallationHistoryAction historyAction = new InstallationHistoryAction(outputPath, new AcceptingConsole());
        final List<SavedState> revisions = historyAction.getRevisions();

        final SavedState savedState = revisions.get(1);
        historyAction.prepareRevert(savedState, mavenOptions, Collections.emptyList(), candidate);

        wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Reverted jar should be present in module", wildflyCliModulePath.resolve(UPGRADE_JAR).toFile().exists());

        manifestVersionRecord = ManifestVersionRecord.read(
                candidate.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(ProsperoMetadataUtils.CURRENT_VERSION_FILE));
        assertTrue("Manifest version record should be present", manifestVersionRecord.isPresent());
        assertEquals("Manifest record version should be restored to initial installation (single channel)",
                1, manifestVersionRecord.get().getUrlManifests().size());

        // assert we don't have ROLLBACK state
        assertThat(historyAction.getRevisions())
                .map(SavedState::getType)
                .contains(SavedState.Type.UPDATE, SavedState.Type.INSTALL);
    }

    @Test
    public void prepareRevertWithoutInitialManifestVersionsRemovesVersionRecord() throws Exception {
        channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);
        final Path modulesPaths = outputPath.resolve(Paths.get("modules", "system", "layers", "base"));
        final Path wildflyCliModulePath = modulesPaths.resolve(Paths.get("org", "jboss", "as", "cli", "main"));
        final Path candidate = temp.newFolder().toPath();

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        // delete the .installation folder and recreate it
        try (final InstallationMetadata oldMetadata = InstallationMetadata.loadInstallation(outputPath)) {
            FileUtils.deleteQuietly(outputPath.resolve(ProsperoMetadataUtils.METADATA_DIR).toFile());
            InstallationMetadata.newInstallation(outputPath, oldMetadata.getManifest(), oldMetadata.getProsperoConfig(),
                    Optional.empty()).recordProvision(true);
        }

        MetadataTestUtils.prepareChannel(outputPath.resolve(MetadataTestUtils.INSTALLER_CHANNELS_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        updateAction().performUpdate();
        Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Updated jar should be present in module", wildflyCliModulePath.resolve(UPGRADE_JAR).toFile().exists());

        Optional<ManifestVersionRecord> manifestVersionRecord = ManifestVersionRecord.read(
                outputPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(ProsperoMetadataUtils.CURRENT_VERSION_FILE));
        assertTrue("Manifest version record should be present", manifestVersionRecord.isPresent());
        assertEquals("Manifest version record should contain base and update channels",
                2, manifestVersionRecord.get().getUrlManifests().size());

        final InstallationHistoryAction historyAction = new InstallationHistoryAction(outputPath, new AcceptingConsole());
        final List<SavedState> revisions = historyAction.getRevisions();

        final SavedState savedState = revisions.get(1);
        historyAction.prepareRevert(savedState, mavenOptions, Collections.emptyList(), candidate);

        wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Reverted jar should be present in module", wildflyCliModulePath.resolve(UPGRADE_JAR).toFile().exists());

        manifestVersionRecord = ManifestVersionRecord.read(
                candidate.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(ProsperoMetadataUtils.CURRENT_VERSION_FILE));
        assertTrue("Manifest version record should not be present", manifestVersionRecord.isEmpty());

        // assert we don't have ROLLBACK state
        assertThat(historyAction.getRevisions())
                .map(SavedState::getType)
                .contains(SavedState.Type.UPDATE, SavedState.Type.INSTALL);
    }

    @Test
    public void rollbackChangesWithTemporaryRepo() throws Exception {
        // install server
        final Path manifestPath = temp.newFile().toPath();
        channelsFile = temp.newFile().toPath();
        MetadataTestUtils.copyManifest("manifests/wfcore-base.yaml", manifestPath);
        MetadataTestUtils.prepareChannel(channelsFile, List.of(manifestPath.toUri().toURL()));

        final Path modulesPaths = outputPath.resolve(Paths.get("modules", "system", "layers", "base"));
        final Path wildflyCliModulePath = modulesPaths.resolve(Paths.get("org", "jboss", "as", "cli", "main"));

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);
        updateAction().performUpdate();
        Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Updated jar should be present in module", wildflyCliModulePath.resolve(UPGRADE_JAR).toFile().exists());

        final InstallationHistoryAction historyAction = new InstallationHistoryAction(outputPath, new AcceptingConsole());
        final List<SavedState> revisions = historyAction.getRevisions();
        final SavedState savedState = revisions.get(1);

        // perform the rollback using temporary repository only. Offline mode disables other repositories
        final URL temporaryRepo = mockTemporaryRepo(false);
        final MavenOptions offlineOptions = MavenOptions.OFFLINE;
        historyAction.rollback(savedState, offlineOptions, List.of(new Repository("temp-repo", temporaryRepo.toExternalForm())));

        wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Reverted jar should be present in module", wildflyCliModulePath.resolve(BASE_JAR).toFile().exists());
        assertThat(ProsperoConfig.readConfig(outputPath.resolve(ProsperoMetadataUtils.METADATA_DIR)).getChannels())
                .withFailMessage("Temporary repository should not be listed")
                .flatMap(Channel::getRepositories)
                .map(Repository::getUrl)
                .doesNotContain(temporaryRepo.toExternalForm());
    }

    @Test
    public void rollbackChangesUsesRestoredChannels() throws Exception {
        // install server
        final Path manifestPath = temp.newFile().toPath();
        channelsFile = temp.newFile().toPath();
        MetadataTestUtils.copyManifest("manifests/wfcore-base.yaml", manifestPath);
        MetadataTestUtils.prepareChannel(channelsFile, List.of(manifestPath.toUri().toURL()));

        final Path modulesPaths = outputPath.resolve(Paths.get("modules", "system", "layers", "base"));
        final Path wildflyCliModulePath = modulesPaths.resolve(Paths.get("org", "jboss", "as", "cli", "main"));

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);
        updateAction().performUpdate();
        Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Updated jar should be present in module", wildflyCliModulePath.resolve(UPGRADE_JAR).toFile().exists());

        // create a "broken" channel that would prevent revert if the latest avilable configuration is used
        final InstallationMetadata metadata = InstallationMetadata.loadInstallation(outputPath);
        final ProsperoConfig prosperoConfig = metadata.getProsperoConfig();
        prosperoConfig.getChannels().add(new Channel.Builder()
                .setName("idontexist")
                .setManifestCoordinate("idont", "exit", "1.0.0")
                .addRepository("test", "http://test.te")
                .build());
        metadata.updateProsperoConfig(prosperoConfig);

        final InstallationHistoryAction historyAction = new InstallationHistoryAction(outputPath, new AcceptingConsole());
        final List<SavedState> revisions = historyAction.getRevisions();
        final SavedState savedState = revisions.get(2);

        // perform the rollback using temporary repository only. Offline mode disables other repositories
        final URL temporaryRepo = mockTemporaryRepo(false);
        final MavenOptions offlineOptions = MavenOptions.OFFLINE;
        historyAction.rollback(savedState, offlineOptions, List.of(new Repository("temp-repo", temporaryRepo.toExternalForm())));

        wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Reverted jar should be present in module", wildflyCliModulePath.resolve(BASE_JAR).toFile().exists());
        assertThat(ProsperoConfig.readConfig(outputPath.resolve(ProsperoMetadataUtils.METADATA_DIR)).getChannels())
                .withFailMessage("Temporary repository should not be listed")
                .flatMap(Channel::getRepositories)
                .map(Repository::getUrl)
                .doesNotContain(temporaryRepo.toExternalForm());
    }

    @Test
    public void displayChanges() throws Exception {
        channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.prepareChannel(outputPath.resolve(MetadataTestUtils.INSTALLER_CHANNELS_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        updateAction().performUpdate();

        final InstallationHistoryAction historyAction = new InstallationHistoryAction(outputPath, new AcceptingConsole());
        final List<SavedState> revisions = historyAction.getRevisions();

        final SavedState savedState = revisions.get(0);
        // make sure the test name is checked not whole state
        final List<ArtifactChange> changes = historyAction.compare(new SavedState(savedState.getName())).getArtifactChanges();

        assertEquals(1, changes.size());

        Map<String, String[]> expected = new HashMap<>();
        expected.put("org.wildfly.core:wildfly-cli", new String[]{BASE_VERSION, UPGRADE_VERSION});

        for (ArtifactChange change : changes) {
            if (expected.containsKey(change.getArtifactName())) {
                final String[] versions = expected.get(change.getArtifactName());
                assertEquals(versions[1], change.getNewVersion().get());
                expected.remove(change.getArtifactName());
            } else {
                Assert.fail("Unexpected artifact in updates " + change);
            }
        }
        assertEquals("Not all expected changes were listed", 0, expected.size());
    }

    private UpdateAction updateAction() throws ProvisioningException, OperationException {
        return new UpdateAction(outputPath, mavenOptions, new AcceptingConsole(), Collections.emptyList());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException, MetadataException {
        final File manifestFile = outputPath.resolve(MetadataTestUtils.MANIFEST_FILE_PATH).toFile();
        return ManifestYamlSupport.parse(manifestFile).getStreams()
                .stream().filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }
}
