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

package org.wildfly.prospero.metadata;

import org.jboss.galleon.util.HashUtils;
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
import org.wildfly.channel.Repository;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ManifestVersionResolverTest {

    @Mock
    private VersionResolverFactory factory;
    @Mock
    private MavenVersionsResolver mavenResolver;
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Before
    public void setUp() {
        when(factory.create(any())).thenReturn(mavenResolver);
    }

    @Test
    public void getVersionOfLatestMavenManifest() throws Exception {
        final ManifestVersionResolver resolver = new ManifestVersionResolver(factory);

        final File manifestFile = temp.newFile();
        final URL url = manifestFile.toURI().toURL();
        Files.writeString(manifestFile.toPath(), ChannelManifestMapper.toYaml(new ChannelManifest("test", null, null, Collections.emptyList())));

        when(mavenResolver.getAllVersions("org.test", "test", "yaml", "manifest"))
                .thenReturn(Set.of("1.0.0", "1.0.1"));
        when(mavenResolver.resolveChannelMetadata(any()))
                .thenReturn(List.of(url));

        final Channel channel = new Channel("test", "", null, Collections.emptyList(),
                new ChannelManifestCoordinate("org.test", "test"), null, null);
        final ManifestVersionRecord currentVersions = resolver.getCurrentVersions(List.of(channel));

        assertThat(currentVersions.getMavenManifests())
                .map(ManifestVersionRecord.MavenManifest::getVersion)
                .containsExactly("1.0.1");
        assertThat(currentVersions.getMavenManifests())
                .map(ManifestVersionRecord.MavenManifest::getDescription)
                .containsExactly("test");
        assertThat(currentVersions.getOpenManifests()).isEmpty();
        assertThat(currentVersions.getUrlManifests()).isEmpty();
    }

    @Test
    public void getHardcodedVersionOfManifestIfPresent() throws Exception {
        final ManifestVersionResolver resolver = new ManifestVersionResolver(factory);

        final Channel channel = new Channel("test", "", null, Collections.emptyList(),
                new ChannelManifestCoordinate("org.test", "test", "1.0.0"), null, null);
        final ManifestVersionRecord currentVersions = resolver.getCurrentVersions(List.of(channel));

        assertThat(currentVersions.getMavenManifests())
                .map(ManifestVersionRecord.MavenManifest::getVersion)
                .containsExactly("1.0.0");
        assertThat(currentVersions.getOpenManifests()).isEmpty();
        assertThat(currentVersions.getUrlManifests()).isEmpty();
    }

    @Test
    public void emptyVersionIfLatestVersionCannotBeFound() throws Exception {
        final ManifestVersionResolver resolver = new ManifestVersionResolver(factory);

        when(mavenResolver.getAllVersions("org.test", "test", "yaml", "manifest"))
                .thenReturn(Collections.emptySet());

        final Channel channel = new Channel("test", "", null, Collections.emptyList(),
                new ChannelManifestCoordinate("org.test", "test"), null, null);
        final ManifestVersionRecord currentVersions = resolver.getCurrentVersions(List.of(channel));

        assertThat(currentVersions.getMavenManifests())
                .map(ManifestVersionRecord.MavenManifest::getVersion)
                .containsExactly("");
        assertThat(currentVersions.getMavenManifests())
                .map(ManifestVersionRecord.MavenManifest::getGroupId)
                .containsExactly("org.test");
        assertThat(currentVersions.getMavenManifests())
                .map(ManifestVersionRecord.MavenManifest::getArtifactId)
                .containsExactly("test");
        assertThat(currentVersions.getOpenManifests()).isEmpty();
        assertThat(currentVersions.getUrlManifests()).isEmpty();
    }

    @Test
    public void getUrlAndHashOfManifest() throws Exception {
        final ManifestVersionResolver resolver = new ManifestVersionResolver(factory);

        final File manifestFile = temp.newFile();
        final URL url = manifestFile.toURI().toURL();
        Files.writeString(manifestFile.toPath(), ChannelManifestMapper.toYaml(new ChannelManifest("test", null, null, Collections.emptyList())));
        final Channel channel = new Channel("test", "", null, Collections.emptyList(),
                new ChannelManifestCoordinate(url), null, null);
        final ManifestVersionRecord currentVersions = resolver.getCurrentVersions(List.of(channel));

        assertThat(currentVersions.getMavenManifests()).isEmpty();
        assertThat(currentVersions.getOpenManifests()).isEmpty();
        assertThat(currentVersions.getUrlManifests())
                .map(ManifestVersionRecord.UrlManifest::getUrl)
                .containsExactly(url.toExternalForm());
        assertThat(currentVersions.getUrlManifests())
                .map(ManifestVersionRecord.UrlManifest::getHash)
                .containsExactly(HashUtils.hashFile(Path.of(url.toURI())));
        assertThat(currentVersions.getUrlManifests())
                .map(ManifestVersionRecord.UrlManifest::getDescription)
                .containsExactly("test");
    }

    @Test
    public void getListOfReposInOpenManifest() throws Exception {
        final ManifestVersionResolver resolver = new ManifestVersionResolver(factory);

        final Channel channel = new Channel("test", "", null, List.of(new Repository("test-repo", "")),
                null, null, Channel.NoStreamStrategy.LATEST);
        final ManifestVersionRecord currentVersions = resolver.getCurrentVersions(List.of(channel));

        assertThat(currentVersions.getMavenManifests()).isEmpty();
        assertThat(currentVersions.getUrlManifests()).isEmpty();
        assertThat(currentVersions.getOpenManifests())
                .flatMap(ManifestVersionRecord.NoManifest::getRepos)
                .containsExactly("test-repo");
        assertThat(currentVersions.getOpenManifests())
                .map(ManifestVersionRecord.NoManifest::getStrategy)
                .containsExactly(Channel.NoStreamStrategy.LATEST.toString());
    }

    @Test
    public void emptyChannelListMapsToEmptyVersionRecord() throws Exception {
        final ManifestVersionResolver resolver = new ManifestVersionResolver(factory);

        final ManifestVersionRecord currentVersions = resolver.getCurrentVersions(Collections.emptyList());

        assertThat(currentVersions.getMavenManifests()).isEmpty();
        assertThat(currentVersions.getUrlManifests()).isEmpty();
        assertThat(currentVersions.getOpenManifests()).isEmpty();

    }

}