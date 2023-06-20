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

package org.wildfly.prospero.updates;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.ProvisioningManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.ArtifactTransferException;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.VersionResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UpdateFinderTest {

    @Mock
    ChannelSession channelSession;
    @Mock
    ProvisioningManager provMgr;

    @Test
    public void testDowngradeIsPossible() throws Exception {
        when(channelSession.findLatestMavenArtifactVersion("org.foo", "bar", "jar", "", null))
                .thenReturn(new VersionResult("1.0.0", null));

        UpdateFinder finder = new UpdateFinder(channelSession);
        final List<Artifact> artifacts = Arrays.asList(
                new DefaultArtifact("org.foo", "bar", "jar", "1.0.1")
                );
        final UpdateSet updates = finder.findUpdates(artifacts);

        assertEquals(1, updates.getArtifactUpdates().size());
        assertEquals("org.foo:bar", updates.getArtifactUpdates().get(0).getArtifactName());
        assertEquals("1.0.0", updates.getArtifactUpdates().get(0).getNewVersion().get());
        assertEquals("1.0.1", updates.getArtifactUpdates().get(0).getOldVersion().get());
    }

    @Test
    public void testExcludeSameVersion() throws Exception {
        when(channelSession.findLatestMavenArtifactVersion("org.foo", "bar", "jar", "", null))
                .thenReturn(new VersionResult("1.0.0", null));

        UpdateFinder finder = new UpdateFinder(channelSession);
        final List<Artifact> artifacts = Arrays.asList(
                new DefaultArtifact("org.foo", "bar", "jar", "1.0.0")
        );
        final UpdateSet updates = finder.findUpdates(artifacts);

        assertEquals(0, updates.getArtifactUpdates().size());
    }

    @Test
    public void testIncludeUpgradeVersion() throws Exception {
        when(channelSession.findLatestMavenArtifactVersion("org.foo", "bar", "jar", "", null))
                .thenReturn(new VersionResult("1.0.1", null));

        UpdateFinder finder = new UpdateFinder(channelSession);
        final List<Artifact> artifacts = Arrays.asList(
                new DefaultArtifact("org.foo", "bar", "jar", "1.0.0")
        );
        final UpdateSet updates = finder.findUpdates(artifacts);

        assertEquals(1, updates.getArtifactUpdates().size());
        assertEquals("org.foo:bar", updates.getArtifactUpdates().get(0).getArtifactName());
        assertEquals("1.0.1", updates.getArtifactUpdates().get(0).getNewVersion().get());
        assertEquals("1.0.0", updates.getArtifactUpdates().get(0).getOldVersion().get());
    }

    @Test
    public void testRemoval() throws Exception {
        when(channelSession.findLatestMavenArtifactVersion("org.foo", "bar", "jar", "", null))
                .thenThrow(new ArtifactTransferException("Exception", Collections.emptySet(), Collections.emptySet()));

        UpdateFinder finder = new UpdateFinder(channelSession);
        final List<Artifact> artifacts = Arrays.asList(
                new DefaultArtifact("org.foo", "bar", "jar", "1.0.0")
        );
        final UpdateSet updates = finder.findUpdates(artifacts);

        assertEquals(1, updates.getArtifactUpdates().size());
        assertEquals("org.foo:bar", updates.getArtifactUpdates().get(0).getArtifactName());
        assertEquals(Optional.empty(), updates.getArtifactUpdates().get(0).getNewVersion());
        assertEquals("1.0.0", updates.getArtifactUpdates().get(0).getOldVersion().get());
    }
}