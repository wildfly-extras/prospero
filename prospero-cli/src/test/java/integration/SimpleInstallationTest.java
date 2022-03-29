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

import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.actions.Installation;
import com.redhat.prospero.actions.Update;
import com.redhat.prospero.api.ProvisioningDefinition;
import com.redhat.prospero.cli.CliConsole;
import com.redhat.prospero.model.ManifestYamlSupport;
import com.redhat.prospero.wfchannel.MavenSessionManager;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.jboss.galleon.layout.FeaturePackUpdatePlan;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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

import static org.junit.Assert.*;

/*
 * Currently requires a local repos to run
 * TODO: use channels based on central repo once the metadata issue is fixed (MVNCENTRAL-7012)
 */
public class SimpleInstallationTest {

    private static final String OUTPUT_DIR = "target/server";
    private Path OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();
    private Path manifestPath = OUTPUT_PATH.resolve(TestUtil.MANIFEST_FILE_PATH);
    private MavenSessionManager mavenSessionManager = new MavenSessionManager();
    private Installation installation = new Installation(OUTPUT_PATH, mavenSessionManager, new CliConsole());

    public SimpleInstallationTest() throws Exception {
    }

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
        final Path channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");

        final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
           .setFpl("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final")
           .setChannelsFile(channelFile)
           .build();
        installation.provision(provisioningDefinition);

        // verify installation with manifest file is present
        assertTrue(manifestPath.toFile().exists());
        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.0.Final", wildflyCliArtifact.get().getVersion());


    }

    @Test
    public void updateWildflyCore() throws Exception {
        final Path channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");

        final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
           .setFpl("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final")
           .setChannelsFile(channelFile)
           .build();
        installation.provision(provisioningDefinition);

        TestUtil.prepareChannelFileAsUrl(OUTPUT_PATH.resolve(TestUtil.CHANNELS_FILE_PATH), "local-repo-desc.yaml", "local-updates-repo-desc.yaml");
        new Update(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole()).doUpdateAll();

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.1.Final", wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void updateWildflyCoreDryRun() throws Exception {
        final Path channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");

        final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
           .setFpl("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final")
           .setChannelsFile(channelFile)
           .build();
        installation.provision(provisioningDefinition);

        TestUtil.prepareChannelFileAsUrl(OUTPUT_PATH.resolve(TestUtil.CHANNELS_FILE_PATH), "local-repo-desc.yaml", "local-updates-repo-desc.yaml");
        final Set<String> updates = new HashSet<>();
        new Update(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole() {
            @Override
            public void updatesFound(Collection<FeaturePackUpdatePlan> fpUpdates,
                                     List<ArtifactChange> artifactUpdates) {
                updates.addAll(artifactUpdates.stream().map(ac->ac.getOldVersion().getArtifactId()).collect(Collectors.toSet()));
                super.updatesFound(fpUpdates, artifactUpdates);
            }
        }).listUpdates();

        // verify manifest contains versions 17.0.1
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.0.Final", wildflyCliArtifact.get().getVersion());
        assertEquals(1, updates.size());
        assertEquals("wildfly-cli", updates.stream().findFirst().get());
    }

    @Test
    public void installWildflyCoreFromInstallationFile() throws Exception {
        final Path channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");
        final File installationFile = new File(this.getClass().getClassLoader().getResource("provisioning.xml").toURI());
        final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelFile);

        installation.provision(installationFile.toPath(), channelRefs);

        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.0.Final", wildflyCliArtifact.get().getVersion());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException {
        final File manifestFile = manifestPath.toFile();
//        return ManifestXmlSupport.parse(manifestFile).getArtifacts().stream()
//                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
//                .findFirst();
        return ManifestYamlSupport.parse(manifestFile).getArtifacts().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst();
    }

}
