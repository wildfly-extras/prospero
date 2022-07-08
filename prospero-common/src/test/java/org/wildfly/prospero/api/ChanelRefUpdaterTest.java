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
import org.jboss.galleon.ProvisioningException;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.wfchannel.ChannelRefUpdater;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ChanelRefUpdaterTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test(expected = ArtifactResolutionException.class)
    public void resolveChannelFileThrowsExceptionIfNoVersionsFound() throws Exception {
        // repository "test", this.getClass().getResource("/").toString(),
        final List<RemoteRepository> repositories = Arrays.asList(
                new RemoteRepository.Builder("test", "default", this.getClass().getResource("/").toString())
                        .build());
        final List<ChannelRef> channels = Arrays.asList(new ChannelRef("org.test:test:1.0", null));
        new ChannelRefUpdater(new MavenSessionManager()).resolveLatest(channels, repositories);
    }

    @Test
    public void testResolveChannelWithoutVersion() throws Exception {
        final RemoteRepository testRepo = new RemoteRepository.Builder("test", "default", temp.newFolder().toURI().toURL().toString()).build();
        final File channelFile = temp.newFile("test.yaml");
        Files.writeString(channelFile.toPath(), ChannelMapper.toYaml(new Channel("test", null, null, null, null)));

        deployMockChannel(testRepo, channelFile);

        final List<ChannelRef> channels = Arrays.asList(new ChannelRef("test:channel-one", null));
        final List<ChannelRef> resolved = new ChannelRefUpdater(new MavenSessionManager()).resolveLatest(channels, Arrays.asList(testRepo));
        assertEquals(1, resolved.size());
        assertEquals("test:channel-one:1.0.1", resolved.get(0).getGav());
    }

    @Test
    public void testResolveChannelWithVersion() throws Exception {
        final RemoteRepository testRepo = new RemoteRepository.Builder("test", "default", temp.newFolder().toURI().toURL().toString()).build();
        final File channelFile = temp.newFile("test.yaml");
        Files.writeString(channelFile.toPath(), ChannelMapper.toYaml(new Channel("test", null, null, null, null)));

        deployMockChannel(testRepo, channelFile);

        final List<ChannelRef> channels = Arrays.asList(new ChannelRef("test:channel-one:1.0.0", null));
        final List<ChannelRef> resolved = new ChannelRefUpdater(new MavenSessionManager()).resolveLatest(channels, Arrays.asList(testRepo));
        assertEquals(1, resolved.size());
        assertEquals("test:channel-one:1.0.1", resolved.get(0).getGav());
    }

    private void deployMockChannel(RemoteRepository testRepo, File channelFile) throws ProvisioningException, DeploymentException {
        MavenSessionManager msm = new MavenSessionManager();
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system, false);


        DeployRequest req = new DeployRequest();
        req.setRepository(testRepo);

        req.setArtifacts(Arrays.asList(new DefaultArtifact("test", "channel-one", "channel", "yaml", "1.0.0", null, channelFile)));
        system.deploy(session, req);

        req.setArtifacts(Arrays.asList(new DefaultArtifact("test", "channel-one", "channel", "yaml", "1.0.1", null, channelFile)));
        system.deploy(session, req);
    }
}