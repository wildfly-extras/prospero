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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.ProvisioningException;
import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.test.MetadataTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimpleProvisionTest extends WfCoreTestBase {

    @Test
    public void installWildflyCore() throws Exception {
        final Path channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(List.of(channelsFile.toString()))
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        // verify installation with manifest file is present
        assertTrue(manifestPath.toFile().exists());
        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void installWildflyCore_ChannelsWithEmptyNamesAreNamed() throws Exception {
        final Path channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);

        // make sure the channel names are empty
        ProsperoConfig prosperoConfig = ProsperoConfig.readConfig(channelsFile);
        List<Channel> emptyNameChannels = prosperoConfig.getChannels().stream()
                .map(c -> new Channel(c.getSchemaVersion(), null, null, null, c.getRepositories(),
                        c.getManifestCoordinate(), null, null)).collect(Collectors.toList());
        new ProsperoConfig(emptyNameChannels).writeConfig(channelsFile);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(List.of(channelsFile.toString()))
                .build();

        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        final ProsperoConfig persistedConfig = ProsperoConfig.readConfig(outputPath.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME));
        assertThat(persistedConfig.getChannels())
                .map(Channel::getName)
                .noneMatch(StringUtils::isEmpty)
                .doesNotHaveDuplicates();
    }

    @Test
    public void updateWildflyCore() throws Exception {
        final Path channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(List.of(channelsFile.toString()))
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.prepareChannel(outputPath.resolve(MetadataTestUtils.INSTALLER_CHANNELS_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        getUpdateAction().performUpdate();

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void updateWildflyCoreFp() throws Exception {
        final Path channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(List.of(channelsFile.toString()))
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.prepareChannel(outputPath.resolve(MetadataTestUtils.INSTALLER_CHANNELS_FILE_PATH), CHANNEL_FP_UPDATES, CHANNEL_BASE_CORE_19);
        getUpdateAction().performUpdate();

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-core-galleon-pack");
        assertEquals("19.0.0.Beta12", wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void updateWildflyCoreFp_InstalledWithGAV() throws Exception {
        final Path channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setFpl("org.wildfly.core:wildfly-core-galleon-pack")
                .setChannelCoordinates(List.of(channelsFile.toString()))
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.prepareChannel(outputPath.resolve(MetadataTestUtils.INSTALLER_CHANNELS_FILE_PATH), CHANNEL_FP_UPDATES, CHANNEL_BASE_CORE_19);
        getUpdateAction().performUpdate();

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-core-galleon-pack");
        assertEquals("19.0.0.Beta12", wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void updateWildflyCoreDryRun() throws Exception {
        final Path channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(List.of(channelsFile.toString()))
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        MetadataTestUtils.prepareChannel(outputPath.resolve(MetadataTestUtils.INSTALLER_CHANNELS_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        final Set<String> updates = new UpdateAction(outputPath, mavenSessionManager, new AcceptingConsole(), Collections.emptyList())
                .findUpdates().getArtifactUpdates().stream()
                .map(ArtifactChange::getArtifactName)
                .collect(Collectors.toSet());

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
        assertEquals(1, updates.size());
        assertEquals("org.wildfly.core:wildfly-cli", updates.stream().findFirst().get());
    }

    @Test
    public void installWildflyCoreFromInstallationFile() throws Exception {
        final Path channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);
        final URI installationFile = this.getClass().getClassLoader().getResource("provisioning.xml").toURI();

        ProvisioningDefinition definition = ProvisioningDefinition.builder()
                .setDefinitionFile(installationFile)
                .setChannelCoordinates(List.of(channelsFile.toString()))
                .build();

        installation.provision(definition.toProvisioningConfig(), definition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
    }

    private UpdateAction getUpdateAction() throws ProvisioningException, OperationException {
        return new UpdateAction(outputPath, mavenSessionManager, new AcceptingConsole(), Collections.emptyList());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException {
        final File manifestFile = manifestPath.toFile();
        return ManifestYamlSupport.parse(manifestFile).getStreams().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }

}
