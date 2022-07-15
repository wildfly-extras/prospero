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

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.jboss.galleon.ProvisioningException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class UpdateTest extends WfCoreTestBase {

    private static final String OUTPUT_DIR = "target/server";
    private static final Path OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();
    private static final Path MANIFEST_PATH = OUTPUT_PATH.resolve(MetadataTestUtils.MANIFEST_FILE_PATH);
    private final ProvisioningAction installation = new ProvisioningAction(OUTPUT_PATH, mavenSessionManager, new CliConsole());

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private File mockRepo;

    @Before
    public void setUp() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
        mockRepo = temp.newFolder("repo");
    }

    @After
    public void tearDown() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
    }

    @Test
    public void updateWildflyCoreWithNewChannel() throws Exception {
        // deploy channel file
        File channelFile = new File(MetadataTestUtils.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19).toURI());
        deployChannelFile(channelFile, "1.0.0");

        // provision using channel gav
        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(buildConfigWithMockRepo().toPath())
                .build();
        installation.provision(provisioningDefinition);

        Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());

        // update channel file
        final File updatedChannel = upgradeTestArtifactIn(channelFile);
        deployChannelFile(updatedChannel, "1.0.1");

        // update installation
        new UpdateAction(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole()).doUpdateAll(false);

        wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
    }

    private File upgradeTestArtifactIn(File channelFile) throws IOException {
        final Channel channel = ManifestYamlSupport.parse(channelFile);
        final List<Stream> streams = channel.getStreams().stream().map(s -> {
            if (s.getGroupId().equals("org.wildfly.core") && s.getArtifactId().equals("wildfly-cli")) {
                return new Stream(s.getGroupId(), s.getArtifactId(), UPGRADE_VERSION);
            }
            return s;
        }).collect(Collectors.toList());

        final File file = temp.newFile("test-channel.yaml");
        ManifestYamlSupport.write(new Channel(channel.getSchemaVersion(), channel.getName(), null, null, null, streams),
                file.toPath());
        return file;
    }

    private File buildConfigWithMockRepo() throws IOException {
        final List<RepositoryRef> repositories = new ArrayList<>(defaultRemoteRepositories());
        final File configFile = temp.newFile("prospero-config.yaml");
        repositories.add(new RepositoryRef("test-repo", mockRepo.toURI().toURL().toString()));
        new ProsperoConfig(Arrays.asList(new ChannelRef("test:channel", null)), repositories).writeConfig(configFile);
        return configFile;
    }

    private void deployChannelFile(File channelFile, String version) throws ProvisioningException, MalformedURLException, DeploymentException {
        final MavenSessionManager msm = new MavenSessionManager();
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system);
        final DeployRequest request = new DeployRequest();
        request.setRepository(new RepositoryRef("test-repo", mockRepo.toURI().toURL().toString()).toRemoteRepository());
        request.setArtifacts(Arrays.asList(new DefaultArtifact("test", "channel", "channel", "yaml", version,
                null, channelFile)));
        system.deploy(session, request);
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException {
        final File manifestFile = MANIFEST_PATH.toFile();
        return ManifestYamlSupport.parse(manifestFile).getStreams().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }

}
