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

package org.wildfly.prospero.patch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProvisioningConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ArchiveInstallerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    @Mock
    private InstallationMetadata metadata;
    @Captor
    private ArgumentCaptor<ProvisioningConfig> updatedConfigCaptor;
    @InjectMocks
    private ArchiveInstaller installer;

    @Test
    public void findPatchFileInArchive() throws Exception {
        // mock up server
        final Path server = temp.newFolder("server-base").toPath();
        Files.createDirectory(server.resolve(".installation"));
        final List<ChannelRef> channels = new ArrayList<>();
        channels.add(new ChannelRef("foo:bar", null));
        ProvisioningConfig config = new ProvisioningConfig(channels, new ArrayList<>());
        config.writeConfig(server.resolve(".installation").resolve("prospero-config.yaml").toFile());
        when(metadata.getProsperoConfig()).thenReturn(config);
        doNothing().when(metadata).updateProsperoConfig(updatedConfigCaptor.capture());

        // create archive
        final File patchArchive = createPatchArchive();

        // install
        final URL patchFileUrl = installer.install(patchArchive, server);

        // should have .patches/channels with channel file and .patches/repository with the repository content
        assertTrue(Files.exists(server.resolve(".patches").resolve("patch-test00001-channel.yaml")));
        assertEquals(server.resolve(".patches").resolve("patch-test00001-channel.yaml").toUri().toURL(), patchFileUrl);
        assertTrue(Files.exists(server.resolve(".patches").resolve(Paths.get("repository/foo/bar/test/1.2.3/test-1.2.3.jar"))));

        final ProvisioningConfig updatedConfig = updatedConfigCaptor.getValue();
        assertEquals(2, updatedConfig.getChannels().size());
        assertEquals(patchFileUrl.toString(), updatedConfig.getChannels().get(0).getUrl());
        assertEquals("foo:bar", updatedConfig.getChannels().get(1).getGav());

        assertEquals(1, updatedConfig.getRepositories().size());
        assertEquals(server.resolve(".patches").resolve("repository").toUri().toURL().toString(), updatedConfig.getRepositories().get(0).getUrl());
    }

    // TODO: test target repository non-empty

    private File createPatchArchive() throws Exception {
        Channel channel = new Channel("patch-test00001", null, null, null,
                Arrays.asList(new Stream("foo.bar", "test", "1.2.3")));

        final File artifact = temp.newFile("test-1.2.3.jar");

        final File archive = temp.newFile("patch.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archive))) {
            zos.putNextEntry(new ZipEntry("patch-test00001-channel.yaml"));
            String channelStr = ChannelMapper.toYaml(channel);
            zos.write(channelStr.getBytes(StandardCharsets.UTF_8), 0, channelStr.length());


            zos.putNextEntry(new ZipEntry("repository/"));
            zos.putNextEntry(new ZipEntry("repository/foo/"));
            zos.putNextEntry(new ZipEntry("repository/foo/bar/"));
            zos.putNextEntry(new ZipEntry("repository/foo/bar/test/1.2.3/"));
            zos.putNextEntry(new ZipEntry("repository/foo/bar/test/1.2.3/test-1.2.3.jar"));
            try(FileInputStream fis = new FileInputStream(artifact)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.write(channelStr.getBytes(StandardCharsets.UTF_8), 0, channelStr.length());
        }

        return archive;
    }
}
