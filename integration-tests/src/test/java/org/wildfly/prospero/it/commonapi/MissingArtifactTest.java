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

package org.wildfly.prospero.it.commonapi;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertThrows;

public class MissingArtifactTest extends WfCoreTestBase {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void missingArtifactInCopyTask() throws Exception {
        final ChannelManifest source = ChannelManifestMapper.from(MissingArtifactTest.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19));
        final ChannelManifest target = new ChannelManifest(source.getSchemaVersion(), source.getName(), source.getDescription(),
                source.getStreams().stream()
                        .map(s->{
                            if (s.getArtifactId().equals("jboss-modules")) {
                                return new Stream(s.getGroupId(), s.getArtifactId(), "2.0.3.Final-idontexist");
                            } else {
                                return s;
                            }
                        })
                        .collect(Collectors.toList()));
        final Path channelFile = temporaryFolder.newFile().toPath();
        Files.writeString(channelFile, ChannelManifestMapper.toYaml(target));
        final Path provisionConfigFile = temporaryFolder.newFile().toPath();
        MetadataTestUtils.prepareProvisionConfig(provisionConfigFile, List.of(channelFile.toUri().toURL()));

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        assertThrows(ArtifactResolutionException.class, () -> installation.provision(provisioningDefinition));
    }

    @Test
    public void missingArtifactInPackages() throws Exception {
        final ChannelManifest source = ChannelManifestMapper.from(MissingArtifactTest.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19));
        final ChannelManifest target = new ChannelManifest(source.getSchemaVersion(), source.getName(), source.getDescription(),
                source.getStreams().stream()
                        .map(s->{
                            if (s.getArtifactId().equals("remoting-jmx")) {
                                return new Stream(s.getGroupId(), s.getArtifactId(), "3.0.4.Final-idontexist");
                            } else {
                                return s;
                            }
                        })
                        .collect(Collectors.toList()));
        final Path channelFile = temporaryFolder.newFile().toPath();
        Files.writeString(channelFile, ChannelManifestMapper.toYaml(target));
        final Path provisionConfigFile = temporaryFolder.newFile().toPath();
        MetadataTestUtils.prepareProvisionConfig(provisionConfigFile, List.of(channelFile.toUri().toURL()));

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        assertThrows(ArtifactResolutionException.class, () -> installation.provision(provisioningDefinition));
    }
}
