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
package org.wildfly.prospero.api;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.wfchannel.ChannelRefMapper;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ChanelRefMapperTest {

    private final MavenSessionManager msm = new MavenSessionManager();
    private final RepositorySystem system = msm.newRepositorySystem();
    private final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system, false);
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    public ChanelRefMapperTest() throws Exception {

    }

    @Test
    public void resolveChannelFileThrowsExceptionIfNoVersionsFound() throws Exception {
        final List<ChannelRef> channels = Arrays.asList(new ChannelRef("org.test:test:1.0", null));
        try {
            resolveChannel(new RemoteRepository.Builder("test", "default", this.getClass().getResource("/").toString())
                    .build(), channels);
        } catch (ArtifactResolutionException e) {
            assertEquals(Messages.MESSAGES.artifactNotFound("org.test", "test", new Exception()).getMessage(), e.getMessage());
        }
    }

    @Test
    public void testResolveChannelWithoutVersion() throws Exception {
        final RemoteRepository testRepo = new RemoteRepository.Builder("test", "default", temp.newFolder().toURI().toURL().toString()).build();

        deployChannel("test100", "1.0.0", system, session, testRepo);
        deployChannel("test101", "1.0.1", system, session, testRepo);

        final List<ChannelRef> channels = Arrays.asList(new ChannelRef("test:channel-one", null));
        final List<Channel> resolved = resolveChannel(testRepo, channels);
        assertEquals(1, resolved.size());
        assertEquals("test101", resolved.get(0).getName());
    }

    @Test
    public void testResolveChannelWithVersion() throws Exception {
        final RemoteRepository testRepo = new RemoteRepository.Builder("test", "default", temp.newFolder().toURI().toURL().toString()).build();

        deployChannel("test100", "1.0.0", system, session, testRepo);
        deployChannel("test101", "1.0.1", system, session, testRepo);

        final List<ChannelRef> channels = Arrays.asList(new ChannelRef("test:channel-one:1.0.0", null));
        final List<Channel> resolved = resolveChannel(testRepo, channels);
        assertEquals(1, resolved.size());
        assertEquals("test101", resolved.get(0).getName());
    }

    @Test
    public void testIssue155() throws Exception {
        final RemoteRepository testRepo = new RemoteRepository.Builder("test", "default", temp.newFolder().toURI().toURL().toString()).build();

        deployChannel("test-1", "1.0.0.Beta-redhat-00001", system, session, testRepo);
        deployChannel("test-2", "1.0.0.Beta1-redhat-20220915", system, session, testRepo);
        deployChannel("test-3", "1.0.0.Beta1-redhat-20220926", system, session, testRepo);

        final List<ChannelRef> channels = Arrays.asList(new ChannelRef("test:channel-one", null));
        final List<Channel> resolved = resolveChannel(testRepo, channels);
        assertEquals(1, resolved.size());
        assertEquals("test-3", resolved.get(0).getName());
    }

    private void deployChannel(String name, String version, RepositorySystem system, DefaultRepositorySystemSession session, RemoteRepository testRepo) throws IOException, DeploymentException {
        String fileNamee = name + ".yaml";

        DeployRequest req = new DeployRequest();
        req.setRepository(testRepo);

        final File channel1File = temp.newFile(fileNamee);
        Files.writeString(channel1File.toPath(), ChannelMapper.toYaml(new Channel(name, null, null, null, null)));
        req.setArtifacts(Arrays.asList(new DefaultArtifact("test", "channel-one", "channel", "yaml", version, null, channel1File)));
        system.deploy(session, req);
    }

    private List<Channel> resolveChannel(RemoteRepository testRepo, List<ChannelRef> channels) throws Exception {
        final MavenSessionManager msm = new MavenSessionManager();
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system);
        final VersionResolverFactory factory = new VersionResolverFactory(system, session, Arrays.asList(testRepo));
        return new ChannelRefMapper(factory).mapToChannel(channels);
    }
}