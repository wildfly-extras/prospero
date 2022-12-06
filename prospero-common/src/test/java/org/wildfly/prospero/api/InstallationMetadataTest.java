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

package org.wildfly.prospero.api;

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
import org.wildfly.prospero.installation.git.GitStorage;
import org.wildfly.prospero.model.ProsperoConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class InstallationMetadataTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    @Mock
    private GitStorage gitStorage;
    private InstallationMetadata installationMetadata;
    private Path base;

    @Before
    public void setUp() throws Exception {
        base = mockServer();
        installationMetadata = new InstallationMetadata(base, gitStorage);
    }

    @Test
    public void testUpdateProsperoConfig() throws Exception {
        final ProsperoConfig config = installationMetadata.getProsperoConfig();
        Channel channel = new Channel("test", null, null, null,
                List.of(new Repository("test", "file://foo.bar")),
                ArtifactUtils.manifestCoordFromString("new:channel"));
        config.getChannels().add(channel);

        installationMetadata.updateProsperoConfig(config);

        // verify new Channel and Repo in there
        final ProsperoConfig updatedConfig = installationMetadata.getProsperoConfig();
        assertEquals(2, updatedConfig.getChannels().size());
        assertEquals("new channel should be added in first place",
                "new:channel", updatedConfig.getChannels().get(1).getManifestRef().getGav());
        assertEquals("test", updatedConfig.getChannels().get(1).getRepositories().get(0).getId());
        assertEquals("file://foo.bar", updatedConfig.getChannels().get(1).getRepositories().get(0).getUrl());
        verify(gitStorage).recordConfigChange();
    }

    @Test
    public void dontOverrideProsperoConfigIfItExist() throws Exception {
        final ProsperoConfig config = installationMetadata.getProsperoConfig();
        Channel channel = new Channel("test", null, null, null,
                List.of(new Repository("test", "file://foo.bar")),
                ArtifactUtils.manifestCoordFromString("new:channel"));
        config.getChannels().add(channel);

        installationMetadata.recordProvision(false);

        try (final InstallationMetadata im = new InstallationMetadata(base)) {
            final ProsperoConfig newConfig = im.getProsperoConfig();
            assertThat(newConfig.getChannels())
                .map(channel1 -> channel1.getManifestRef().getGav())
                .doesNotContain(
                    "new:channel"
            );
        }
    }

    @Test
    public void writeProsperoConfigIfItDoesNotExist() throws Exception {
        // throw away mocked installation from setup
        base = temp.newFolder().toPath();
        final Channel channel = new Channel("test", "", null, null,
                List.of(new Repository("test", "file://foo.bar")),
                new ChannelManifestCoordinate("new", "channel"));
        installationMetadata = new InstallationMetadata(base,
                new ChannelManifest(null, null, null),
                List.of(channel)
        );

        installationMetadata.recordProvision(false);

        try (final InstallationMetadata im = new InstallationMetadata(base)) {
            final ProsperoConfig newConfig = im.getProsperoConfig();
            assertEquals(1, newConfig.getChannels().size());
            assertEquals("new:channel", newConfig.getChannels().get(0).getManifestRef().getGav());
            assertEquals("file://foo.bar", newConfig.getChannels().get(0).getRepositories().get(0).getUrl());
            assertEquals("test", newConfig.getChannels().get(0).getRepositories().get(0).getId());
        }
    }

    @Test
    public void initStorageIfItDoesNotExist() throws Exception {
        mockServer();

        new InstallationMetadata(base);

        verify(gitStorage).record();
    }

    private Path mockServer() throws IOException {
        final Path base = temp.newFolder().toPath();
        final Path metadataDir = base.resolve(InstallationMetadata.METADATA_DIR);

        Files.createDirectory(metadataDir);
        Files.writeString(metadataDir.resolve(InstallationMetadata.MANIFEST_FILE_NAME),
                ChannelManifestMapper.toYaml(new ChannelManifest(null, null, Collections.emptyList())),
                StandardOpenOption.CREATE_NEW);
        final Channel channel = new Channel("test", "", null, null,
                List.of(new Repository("test", "file://foo.bar")),
        new ChannelManifestCoordinate("foo","bar"));
        new ProsperoConfig(List.of(channel)).writeConfig(metadataDir.resolve(InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME));
        return base;
    }

}