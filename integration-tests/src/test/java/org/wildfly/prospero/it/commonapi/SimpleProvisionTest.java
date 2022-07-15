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

package org.wildfly.prospero.it.commonapi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.layout.FeaturePackUpdatePlan;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.test.MetadataTestUtils;

import static org.junit.Assert.*;

public class SimpleProvisionTest extends WfCoreTestBase {

    private static final String OUTPUT_DIR = "target/server";
    private static final Path OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();
    private static final Path MANIFEST_PATH = OUTPUT_PATH.resolve(MetadataTestUtils.MANIFEST_FILE_PATH);
    private final ProvisioningAction installation = new ProvisioningAction(OUTPUT_PATH, mavenSessionManager, new CliConsole());

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
    public void installWildflyCore() throws Exception {
        final Path provisionConfigFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        installation.provision(provisioningDefinition);

        // verify installation with manifest file is present
        assertTrue(MANIFEST_PATH.toFile().exists());
        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());


    }

    @Test
    public void updateWildflyCore() throws Exception {
        final Path provisionConfigFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        installation.provision(provisioningDefinition);

        MetadataTestUtils.prepareProvisionConfigAsUrl(OUTPUT_PATH.resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        new UpdateAction(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole()).doUpdateAll(false);

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void updateWildflyCoreFp() throws Exception {
        final Path provisionConfigFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        installation.provision(provisioningDefinition);

        MetadataTestUtils.prepareProvisionConfigAsUrl(OUTPUT_PATH.resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH), CHANNEL_FP_UPDATES, CHANNEL_BASE_CORE_19);
        new UpdateAction(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole()).doUpdateAll(false);

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-core-galleon-pack");
        assertEquals("19.0.0.Beta12", wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void updateWildflyCoreFp_InstalledWithGAV() throws Exception {
        final Path provisionConfigFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setFpl("org.wildfly.core:wildfly-core-galleon-pack")
                .setProvisionConfig(provisionConfigFile)
                .build();
        installation.provision(provisioningDefinition);

        MetadataTestUtils.prepareProvisionConfigAsUrl(OUTPUT_PATH.resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH), CHANNEL_FP_UPDATES, CHANNEL_BASE_CORE_19);
        new UpdateAction(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole()).doUpdateAll(false);

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-core-galleon-pack");
        assertEquals("19.0.0.Beta12", wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void updateWildflyCoreDryRun() throws Exception {
        final Path provisionConfigFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        installation.provision(provisioningDefinition);

        MetadataTestUtils.prepareProvisionConfigAsUrl(OUTPUT_PATH.resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);
        final Set<String> updates = new HashSet<>();
        new UpdateAction(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole() {
            @Override
            public void updatesFound(Collection<FeaturePackUpdatePlan> fpUpdates,
                                     List<ArtifactChange> artifactUpdates) {
                updates.addAll(artifactUpdates.stream().map(ac->ac.getArtifactName()).collect(Collectors.toSet()));
                super.updatesFound(fpUpdates, artifactUpdates);
            }
        }).listUpdates();

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
        assertEquals(1, updates.size());
        assertEquals("org.wildfly.core:wildfly-cli", updates.stream().findFirst().get());
    }

    @Test
    public void installWildflyCoreFromInstallationFile() throws Exception {
        final Path provisionConfigFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);
        final File installationFile = new File(this.getClass().getClassLoader().getResource("provisioning.xml").toURI());
        final ProsperoConfig prosperoConfig = ProsperoConfig.readConfig(provisionConfigFile);
        final List<ChannelRef> channelRefs = prosperoConfig.getChannels();

        installation.provision(installationFile.toPath(), channelRefs,
                repositories.stream().map(RepositoryRef::toRemoteRepository).collect(Collectors.toList()));

        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException {
        final File manifestFile = MANIFEST_PATH.toFile();
        return ManifestYamlSupport.parse(manifestFile).getStreams().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }

}
