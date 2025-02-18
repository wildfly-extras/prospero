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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.ArtifactTransferException;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.RuntimeChannel;
import org.wildfly.channel.Stream;
import org.wildfly.channel.VersionResult;
import org.wildfly.prospero.api.ArtifactChange;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jboss.galleon.api.Provisioning;
import org.wildfly.prospero.api.ChannelVersionChange;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.metadata.ManifestVersionRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@RunWith(MockitoJUnitRunner.class)
public class UpdateFinderTest {

    @Mock
    ChannelSession channelSession;
    @Mock
    InstallationMetadata metadata;
    @Mock
    Provisioning provMgr;

    @Test
    public void testDowngradeIsPossible() throws Exception {
        when(channelSession.findLatestMavenArtifactVersion("org.foo", "bar", "jar", "", null))
                .thenReturn(new VersionResult("1.0.0", null));

        UpdateFinder finder = new UpdateFinder(channelSession, metadata);
        final List<Artifact> artifacts = Arrays.asList(
                new DefaultArtifact("org.foo", "bar", "jar", "1.0.1")
                );
        final UpdateSet updates = finder.findUpdates(artifacts);

        assertEquals(1, updates.getArtifactUpdates().size());
        final ArtifactChange actualChange = updates.getArtifactUpdates().get(0);
        assertEquals("org.foo:bar", actualChange.getArtifactName());
        assertEquals("1.0.0", actualChange.getNewVersion().get());
        assertEquals("1.0.1", actualChange.getOldVersion().get());
        assertEquals(Optional.empty(), actualChange.getChannelName());
    }

    @Test
    public void testExcludeSameVersion() throws Exception {
        when(channelSession.findLatestMavenArtifactVersion("org.foo", "bar", "jar", "", null))
                .thenReturn(new VersionResult("1.0.0", null));

        UpdateFinder finder = new UpdateFinder(channelSession, metadata);
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

        UpdateFinder finder = new UpdateFinder(channelSession, metadata);
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

        UpdateFinder finder = new UpdateFinder(channelSession, metadata);
        final List<Artifact> artifacts = Arrays.asList(
                new DefaultArtifact("org.foo", "bar", "jar", "1.0.0")
        );
        final UpdateSet updates = finder.findUpdates(artifacts);

        assertEquals(1, updates.getArtifactUpdates().size());
        assertEquals("org.foo:bar", updates.getArtifactUpdates().get(0).getArtifactName());
        assertEquals(Optional.empty(), updates.getArtifactUpdates().get(0).getNewVersion());
        assertEquals("1.0.0", updates.getArtifactUpdates().get(0).getOldVersion().get());
    }

    @Test
    public void findUpdatesIncludesChannelNames() throws Exception {
        when(channelSession.findLatestMavenArtifactVersion("org.foo", "bar", "jar", "", null))
                .thenReturn(new VersionResult("1.0.0", "test-channel"));

        UpdateFinder finder = new UpdateFinder(channelSession, metadata);
        final List<Artifact> artifacts = Arrays.asList(
                new DefaultArtifact("org.foo", "bar", "jar", "1.0.1")
        );
        final UpdateSet updates = finder.findUpdates(artifacts);

        assertEquals(1, updates.getArtifactUpdates().size());
        final ArtifactChange actualUpdate = updates.getArtifactUpdates().get(0);
        assertEquals("org.foo:bar", actualUpdate.getArtifactName());
        assertEquals("1.0.0", actualUpdate.getNewVersion().get());
        assertEquals("1.0.1", actualUpdate.getOldVersion().get());
        assertEquals("test-channel", actualUpdate.getChannelName().orElse(null));
    }

    @Test
    public void manifestUpdatesAreAuthoritativeIfAllTheChannelsAreMavenChannels() throws Exception {
        when(channelSession.getRuntimeChannels()).thenReturn(List.of(
           new RuntimeChannel(new Channel.Builder().build(), new ChannelManifest.Builder().build(), null)
        ));
        mockCurrentManifestInformation();
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertTrue(updates.isAuthoritativeManifestVersions());
    }

    @Test
    public void manifestUpdatesAreNotAuthoritativeIfThereAreNoChannelsDefined() throws Exception {
        when(channelSession.getRuntimeChannels()).thenReturn(Collections.emptyList());
        mockCurrentManifestInformation();
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertFalse(updates.isAuthoritativeManifestVersions());
    }

    @Test
    public void manifestUpdatesAreNotAuthoritativeIfOneOfTheChannelsHasNoStreamStrategy() throws Exception {
        when(channelSession.getRuntimeChannels()).thenReturn(List.of(
                new RuntimeChannel(new Channel.Builder()
                        .setResolveStrategy(Channel.NoStreamStrategy.LATEST)
                        .build(), new ChannelManifest.Builder().build(), null)
        ));
        mockCurrentManifestInformation();
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertFalse(updates.isAuthoritativeManifestVersions());
    }

    @Test
    public void manifestUpdatesAreNotAuthoritativeIfOneOfTheChannelsUsesUrlManifest() throws Exception {
        when(channelSession.getRuntimeChannels()).thenReturn(List.of(
                new RuntimeChannel(new Channel.Builder()
                        .setManifestUrl(new URL("http://test.te"))
                        .build(), new ChannelManifest.Builder().build(), null)
        ));
        mockCurrentManifestInformation();
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertFalse(updates.isAuthoritativeManifestVersions());
    }

    @Test
    public void manifestUpdatesAreNotAuthoritativeIfOneOfTheChannelsUsesVersionPatterns() throws Exception {
        when(channelSession.getRuntimeChannels()).thenReturn(List.of(
                new RuntimeChannel(new Channel.Builder().build(),
                        new ChannelManifest.Builder()
                                .addStreams(new Stream("org.foo", "bar", Pattern.compile(".*")))
                                .build(), null)
        ));
        mockCurrentManifestInformation();
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertFalse(updates.isAuthoritativeManifestVersions());
    }

    @Test
    public void manifestUpdatesAreNotAuthoritativeIfUnableToEstablishCurrentVersions() throws Exception {
        when(metadata.getManifestVersions()).thenReturn(Optional.empty());
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertFalse(updates.isAuthoritativeManifestVersions());
    }

    @Test
    public void manifestUpdatesAreNotAuthoritativeIfUnableToEstablishCurrentChannels() throws Exception {
        when(metadata.getManifestVersions()).thenReturn(Optional.of(new ManifestVersionRecord()));
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertFalse(updates.isAuthoritativeManifestVersions());
    }

    @Test
    public void listUpdatedMavenManifest() throws Exception {
        when(channelSession.getRuntimeChannels()).thenReturn(List.of(
                new RuntimeChannel(new Channel.Builder()
                        .setName("test-channel")
                        .setManifestCoordinate("org.foo", "bar", "1.0.1").build(),
                        new ChannelManifest.Builder().build(), null)
        ));
        mockCurrentManifestInformation();
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertThat(updates.getManifestChanges())
                .containsOnly(new ChannelVersionChange.Builder("test-channel")
                        .setOldPhysicalVersion("1.0.0")
                        .setNewPhysicalVersion("1.0.1")
                        .setOldLogicalVersion("Update 1")
                        .build());
    }

    @Test
    public void listsNewMavenManifestAsAdded() throws Exception {
        when(channelSession.getRuntimeChannels()).thenReturn(List.of(
                new RuntimeChannel(new Channel.Builder()
                        .setName("test-channel-1")
                        .setManifestCoordinate("org.foo", "bar", "1.0.1")
                        .build(),
                        new ChannelManifest.Builder().build(), null),
                new RuntimeChannel(new Channel.Builder()
                        .setName("test-channel-2")
                        .setManifestCoordinate("org.foo", "new", "1.0.0")
                        .build(),
                        new ChannelManifest.Builder().build(), null)
                )
        );
        mockCurrentManifestInformation();
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertThat(updates.getManifestChanges())
                .containsOnly(
                        new ChannelVersionChange.Builder("test-channel-1")
                                .setOldPhysicalVersion("1.0.0")
                                .setNewPhysicalVersion("1.0.1")
                                .setOldLogicalVersion("Update 1")
                                .build(),
                        new ChannelVersionChange.Builder("test-channel-2")
                                .setNewPhysicalVersion("1.0.0")
                                .build()
                );
    }

    @Test
    public void listsRemovedMavenManifestAsRemoved() throws Exception {
        when(channelSession.getRuntimeChannels()).thenReturn(Collections.emptyList());
        mockCurrentManifestInformation();
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertThat(updates.getManifestChanges())
                .containsOnly(
                        new ChannelVersionChange.Builder("org.foo:bar") // note we don't have the channel info for removed manifests
                                .setOldPhysicalVersion("1.0.0")
                                .setOldLogicalVersion("Update 1")
                                .build()
                );
    }

    @Test
    public void listsNewMavenManifestWhenNoCurrentInfoAvailable() throws Exception {
        when(channelSession.getRuntimeChannels()).thenReturn(List.of(
                        new RuntimeChannel(new Channel.Builder()
                                .setName("test-channel-1")
                                .setManifestCoordinate("org.foo", "bar", "1.0.1")
                                .build(),
                                new ChannelManifest.Builder().build(), null)
                )
        );
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertThat(updates.getManifestChanges())
                .containsOnly(
                        new ChannelVersionChange.Builder("test-channel-1")
                                .setNewPhysicalVersion("1.0.1")
                                .build()
                );
    }

    @Test
    public void ignoresNonMavenManifest() throws Exception {
        when(channelSession.getRuntimeChannels()).thenReturn(List.of(
                new RuntimeChannel(new Channel.Builder()
                        .setName("test-channel")
                        .setManifestUrl(new URL("http://test.manifest"))
                        .build(),
                        new ChannelManifest.Builder().build(), null)
        ));
        UpdateFinder finder = new UpdateFinder(channelSession, metadata);

        final UpdateSet updates = finder.findUpdates(Collections.emptyList());

        assertThat(updates.getManifestChanges())
                .isEmpty();
    }

    private void mockCurrentManifestInformation() {
        when(metadata.getManifestVersions()).thenReturn(Optional.of(new ManifestVersionRecord("1.0.0", List.of(new ManifestVersionRecord.MavenManifest("org.foo", "bar", "1.0.0" ,"Update 1")), Collections.emptyList(), Collections.emptyList())));
    }
}