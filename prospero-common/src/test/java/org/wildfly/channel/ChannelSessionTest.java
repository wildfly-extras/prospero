/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.channel;

import org.eclipse.aether.RepositorySystem;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ChannelSessionTest {

    @Mock
    private MavenVersionsResolver.Factory factory;

    @Test
    public void testExceptionShouldContainMissingUrlOnManifestNotFound() throws Exception {
        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.OFFLINE_NO_CACHE);
        final RepositorySystem system = msm.newRepositorySystem();
        factory = new VersionResolverFactory(system, msm.newRepositorySystemSession(system));
        Channel channel = new Channel.Builder()
                .setManifestUrl(new URL("file:idontexist.yaml"))
                .build();
        try {
            new ChannelSession(List.of(channel), factory);
            Assert.fail("An exception should have been thrown.");
        } catch (InvalidChannelMetadataException e) {
            Assert.assertTrue(e.getCause() instanceof FileNotFoundException);
            Assert.assertEquals("file:idontexist.yaml" ,e.getValidationMessages().get(0));
        } catch (Exception e) {
            Assert.fail("Expecting " + InvalidChannelMetadataException.class + " but " + e.getClass() + " was thrown");
        }
    }

    @Test
    public void testExceptionShouldContainMissingArtifactOnManifestNotFound() throws Exception {
        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.OFFLINE_NO_CACHE);
        final RepositorySystem system = msm.newRepositorySystem();
        factory = new VersionResolverFactory(system, msm.newRepositorySystemSession(system));
        Channel channel = new Channel.Builder()
                .setManifestCoordinate("foo.bar", "idontexist", "1.0.0")
                .addRepository("test", "file:foobar")
                .build();
        try {
            new ChannelSession(List.of(channel), factory);
            Assert.fail("An exception should have been thrown.");
        } catch (UnresolvedMavenArtifactException e) {
            Assert.assertEquals("idontexist" ,e.getUnresolvedArtifacts().iterator().next().getArtifactId());
            Assert.assertEquals("test" ,e.getAttemptedRepositories().iterator().next().getId());
        } catch (Exception e) {
            Assert.fail("Expecting " + UnresolvedMavenArtifactException.class + " but " + e.getClass() + " was thrown");
        }
    }
}
