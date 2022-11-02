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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.jboss.galleon.ProvisioningException;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.RepositoryUtils;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UpdateTest extends WfCoreTestBase {

    private File mockRepo;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mockRepo = temp.newFolder("repo");
    }

    @Test
    public void updateWildflyCoreWithNewChannel() throws Exception {
        // deploy manifest file
        File manifestFile = new File(MetadataTestUtils.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19).toURI());
        deployManifestFile(manifestFile, "1.0.0");

        // provision using channel gav
        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(buildConfigWithMockRepo().toPath())
                .build();
        installation.provision(provisioningDefinition);

        Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());

        // update manifest file
        final File updatedManifest = upgradeTestArtifactIn(manifestFile);
        deployManifestFile(updatedManifest, "1.0.1");

        // update installation
        new UpdateAction(outputPath, mavenSessionManager, new AcceptingConsole(), Collections.emptyList())
                .performUpdate();

        wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
    }

    @Test
    public void updateWildflyCoreIgnoreChangesInProsperoConfig() throws Exception {
        final Path prosperoConfigFile = outputPath.resolve(InstallationMetadata.METADATA_DIR)
                .resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME);

        // deploy manifest file
        File manifestFile = new File(MetadataTestUtils.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19).toURI());
        deployManifestFile(manifestFile, "1.0.0");

        // provision using manifest gav
        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(buildConfigWithMockRepo().toPath())
                .build();
        installation.provision(provisioningDefinition);

        Files.writeString(prosperoConfigFile, "# test comment", StandardOpenOption.APPEND);

        // update manifest file
        final File updatedChannel = upgradeTestArtifactIn(manifestFile);
        deployManifestFile(updatedChannel, "1.0.1");

        // update installation
        new UpdateAction(outputPath, mavenSessionManager, new AcceptingConsole(), Collections.emptyList())
                .performUpdate();

        assertTrue(Files.readString(prosperoConfigFile).contains("# test comment"));
    }

    private File upgradeTestArtifactIn(File channelFile) throws IOException {
        final ChannelManifest manifest = ManifestYamlSupport.parse(channelFile);
        final List<Stream> streams = manifest.getStreams().stream().map(s -> {
            if (s.getGroupId().equals("org.wildfly.core") && s.getArtifactId().equals("wildfly-cli")) {
                return new Stream(s.getGroupId(), s.getArtifactId(), UPGRADE_VERSION);
            }
            return s;
        }).collect(Collectors.toList());

        final File file = temp.newFile("test-channel.yaml");
        ManifestYamlSupport.write(new ChannelManifest(manifest.getSchemaVersion(), manifest.getName(), null, streams),
                file.toPath());
        return file;
    }

    private File buildConfigWithMockRepo() throws IOException {
        final List<Repository> repositories = new ArrayList<>(defaultRemoteRepositories());
        final File configFile = temp.newFile(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME);
        repositories.add(new Repository("test-repo", mockRepo.toURI().toURL().toString()));
        Channel channel = new Channel("test", "", null, null, repositories,
                new ChannelManifestCoordinate("test", "channel"));
        new ProsperoConfig(List.of(channel)).writeConfig(configFile.toPath());
        return configFile;
    }

    private void deployManifestFile(File channelFile, String version) throws ProvisioningException, MalformedURLException, DeploymentException {
        final MavenSessionManager msm = new MavenSessionManager();
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system);
        final DeployRequest request = new DeployRequest();
        request.setRepository(RepositoryUtils.toRemoteRepository("test-repo", mockRepo.toURI().toURL().toString()));
        request.setArtifacts(Arrays.asList(new DefaultArtifact("test", "channel",
                ChannelManifest.CLASSIFIER, ChannelManifest.EXTENSION, version,
                null, channelFile)));
        system.deploy(session, request);
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException {
        final File manifestFile = manifestPath.toFile();
        return ManifestYamlSupport.parse(manifestFile).getStreams().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }

}
