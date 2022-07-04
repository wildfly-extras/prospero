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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.prospero.installation.git.GitStorage;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class InstallationMetadataTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    @Mock
    private GitStorage gitStorage;
    private InstallationMetadata installationMetadata;

    @Before
    public void setUp() throws Exception {
        final Path base = mockServer();
        installationMetadata = new InstallationMetadata(base, gitStorage);
    }

    @Test
    public void testUpdateProsperoConfig() throws Exception {
        final ProsperoConfig config = installationMetadata.getProsperoConfig();
        config.addChannel(new ChannelRef("new:channel", null));
        config.addRepository(new RepositoryRef("test", "file://foo.bar"));

        installationMetadata.updateProsperoConfig(config);

        // verify new Channel and Repo in there
        final ProsperoConfig updatedConfig = installationMetadata.getProsperoConfig();
        assertEquals(2, updatedConfig.getChannels().size());
        assertEquals("new channel should be added in first place",
                new ChannelRef("new:channel", null), updatedConfig.getChannels().get(0));
        assertEquals(1, updatedConfig.getRepositories().size());
        assertEquals(new RepositoryRef("test", "file://foo.bar"), updatedConfig.getRepositories().get(0));
        verify(gitStorage).recordConfigChange();
    }

    private Path mockServer() throws IOException {
        final Path base = temp.newFolder().toPath();
        final Path metadataDir = base.resolve(InstallationMetadata.METADATA_DIR);

        Files.createDirectory(metadataDir);
        Files.writeString(metadataDir.resolve(InstallationMetadata.MANIFEST_FILE_NAME),
                ChannelMapper.toYaml(new Channel(null, null, null, null, Collections.emptyList())),
                StandardOpenOption.CREATE_NEW);
        new ProsperoConfig(Arrays.asList(new ChannelRef("foo:bar", null)), Collections.emptyList()).writeConfig(
                metadataDir.resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME).toFile());
        return base;
    }

}