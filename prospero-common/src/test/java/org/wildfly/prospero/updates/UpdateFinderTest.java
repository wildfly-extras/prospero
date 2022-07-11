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

package org.wildfly.prospero.updates;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.ProvisioningManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.ChannelSession;

import java.util.Arrays;
import java.util.List;

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
        when(channelSession.findLatestMavenArtifactVersion("org.foo", "bar", "jar", "", null)).thenReturn("1.0.0");

        UpdateFinder finder = new UpdateFinder(channelSession, provMgr);
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
        when(channelSession.findLatestMavenArtifactVersion("org.foo", "bar", "jar", "", null)).thenReturn("1.0.0");

        UpdateFinder finder = new UpdateFinder(channelSession, provMgr);
        final List<Artifact> artifacts = Arrays.asList(
                new DefaultArtifact("org.foo", "bar", "jar", "1.0.0")
        );
        final UpdateSet updates = finder.findUpdates(artifacts);

        assertEquals(0, updates.getArtifactUpdates().size());
    }

    @Test
    public void testIncludeUpgradeVersion() throws Exception {
        when(channelSession.findLatestMavenArtifactVersion("org.foo", "bar", "jar", "", null)).thenReturn("1.0.1");

        UpdateFinder finder = new UpdateFinder(channelSession, provMgr);
        final List<Artifact> artifacts = Arrays.asList(
                new DefaultArtifact("org.foo", "bar", "jar", "1.0.0")
        );
        final UpdateSet updates = finder.findUpdates(artifacts);

        assertEquals(1, updates.getArtifactUpdates().size());
        assertEquals("org.foo:bar", updates.getArtifactUpdates().get(0).getArtifactName());
        assertEquals("1.0.1", updates.getArtifactUpdates().get(0).getNewVersion().get());
        assertEquals("1.0.0", updates.getArtifactUpdates().get(0).getOldVersion().get());
    }
}