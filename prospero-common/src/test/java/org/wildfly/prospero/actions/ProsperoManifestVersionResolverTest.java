/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.actions;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.metadata.ManifestVersionResolver;
import org.wildfly.prospero.wfchannel.ResolvedArtifactsStore;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class ProsperoManifestVersionResolverTest {
    protected static final String A_GROUP_ID = "org.test";
    protected static final String MANIFEST_ONE_ARTIFACT_ID = "manifest-one";
    protected static final String MANIFEST_TWO_ARTIFACT_ID = "manifest-two";
    protected static final String A_VERSION = "1.2.3";
    @Mock
    public ResolvedArtifactsStore artifactVersions;

    @Mock
    public ManifestVersionResolver manifestVersionResolver;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private File manifestFile;
    private ProsperoManifestVersionResolver resolver;

    @Before
    public void setUp() throws Exception {
        manifestFile = temp.newFile("test");
        Files.writeString(manifestFile.toPath(), ChannelManifestMapper.toYaml(
                new ChannelManifest("test", "test", "desc", null)));

        // when the fallback resolver is called return an empty record to make the test pass
        when(manifestVersionResolver.getCurrentVersions(any())).thenReturn(new ManifestVersionRecord());

        resolver = new ProsperoManifestVersionResolver(artifactVersions, manifestVersionResolver);
    }

    @Test
    public void versionResolvedDuringProvisioning() throws Exception {
        whenManifestOneWasResolveDuringProvisioning();

        final ManifestVersionRecord currentVersions = resolver.getCurrentVersions(List.of(new Channel.Builder()
                .setManifestCoordinate(new ChannelManifestCoordinate(A_GROUP_ID, MANIFEST_ONE_ARTIFACT_ID))
                .build()));

        assertThat(currentVersions.getMavenManifests())
                .map(ManifestVersionRecord.MavenManifest::getVersion)
                .containsOnly(A_VERSION);
        assertThat(currentVersions.getMavenManifests())
                .map(ManifestVersionRecord.MavenManifest::getDescription)
                .containsOnly("desc");
    }

    @Test
    public void versionNotResolvedDuringProvisioning_FallsbackToMavenCache() throws Exception {
        when(artifactVersions.getManifestVersion(A_GROUP_ID, MANIFEST_ONE_ARTIFACT_ID)).thenReturn(null);

        final List<Channel> channels = List.of(new Channel.Builder()
                .setManifestCoordinate(new ChannelManifestCoordinate(A_GROUP_ID, MANIFEST_ONE_ARTIFACT_ID))
                .build());
        resolver.getCurrentVersions(channels);

        verify(manifestVersionResolver).getCurrentVersions(channels);
    }

    @Test
    public void alreadyResolvedChannelsAreNotPassedThrough() throws Exception {
        whenManifestOneWasResolveDuringProvisioning();

        final List<Channel> channels = List.of(
                new Channel.Builder()
                        .setManifestCoordinate(new ChannelManifestCoordinate(A_GROUP_ID, MANIFEST_ONE_ARTIFACT_ID))
                        .build(),
                new Channel.Builder()
                        .setManifestCoordinate(new ChannelManifestCoordinate(A_GROUP_ID, MANIFEST_TWO_ARTIFACT_ID))
                        .build());
        resolver.getCurrentVersions(channels);

        verify(manifestVersionResolver).getCurrentVersions(List.of(channels.get(1)));
    }

    @Test
    public void nonOpenMavenChannelsAreResolvedWithFallback() throws Exception {
        whenManifestOneWasResolveDuringProvisioning();

        final List<Channel> channels = List.of(
                new Channel.Builder()
                        .setManifestCoordinate(new ChannelManifestCoordinate(A_GROUP_ID, MANIFEST_ONE_ARTIFACT_ID, A_VERSION))
                        .build(),
                new Channel.Builder()
                        .setManifestUrl(new URI("http://test.te/manifest.yaml").toURL())
                        .build());
        resolver.getCurrentVersions(channels);

        verify(manifestVersionResolver).getCurrentVersions(channels);
    }

    private void whenManifestOneWasResolveDuringProvisioning() {
        when(artifactVersions.getManifestVersion(A_GROUP_ID, MANIFEST_ONE_ARTIFACT_ID)).thenReturn(
                new MavenArtifact(A_GROUP_ID, MANIFEST_ONE_ARTIFACT_ID, null, null, A_VERSION, manifestFile));
    }
}