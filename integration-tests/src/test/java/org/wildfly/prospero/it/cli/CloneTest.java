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

package org.wildfly.prospero.it.cli;

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.actions.InstallationExportAction;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.it.commonapi.WfCoreTestBase;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CloneTest extends WfCoreTestBase {

    private Path targetDir;
    private Path exportPath;
    private Path importDir;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        targetDir = temp.newFolder().toPath();
        exportPath = temp.newFolder("export").toPath().resolve("out.zip");
        importDir = temp.newFolder("restore").toPath().resolve("to_be_restored");
    }

    @After
    public void tearDown() throws Exception {
        if (Files.exists(targetDir)) {
            FileUtils.deleteDirectory(targetDir.toFile());
        }
        if (Files.exists(exportPath)) {
            FileUtils.deleteQuietly(exportPath.toFile());
        }
        if (Files.exists(importDir)) {
            FileUtils.deleteDirectory(importDir.toFile());
        }
    }

    @Test
    public void testExport() throws Exception {
        ChannelManifest installedManifest = install(targetDir, MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19));
        ExecutionUtils.prosperoExecution(CliConstants.Commands.CLONE, CliConstants.Commands.EXPORT,
            CliConstants.DIR, targetDir.toString(),
            CliConstants.ARG_PATH, exportPath.toString()
          )
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);
        checkMetaData(InstallationMetadata.fromMetadataBundle(exportPath), null, installedManifest);
    }

    private ChannelManifest install(Path installDir, Path provisionConfig) throws Exception {
        ExecutionUtils.prosperoExecution(CliConstants.Commands.INSTALL,
                        CliConstants.CHANNEL, provisionConfig.toString(),
                        CliConstants.FPL, "org.wildfly.core:wildfly-core-galleon-pack::zip",
                        CliConstants.DIR, installDir.toString())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);
        return ManifestYamlSupport.parse(installDir.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME).toFile());
    }

    private void checkMetaData(InstallationMetadata metadata, Stream stream, ChannelManifest installedManifest) throws Exception {
        try (InstallationMetadata meta = metadata) {
            ChannelManifest channelManifest = meta.getManifest();
            ProsperoConfig prosperoConfig = meta.getProsperoConfig();
            ProvisioningConfig provisionedConfig = meta.getGalleonProvisioningConfig();
            // check channelManifest
            assertEquals(ChannelManifestMapper.CURRENT_SCHEMA_VERSION, channelManifest.getSchemaVersion());
            assertNull(channelManifest.getDescription());
            assertTrue(channelManifest.getStreams().stream()
              .anyMatch(m -> m.getGroupId().equals("io.undertow")
                && m.getArtifactId().equals("undertow-core")
                && m.getVersion().equals(UNDERTOW_VESION)));
            assertTrue(channelManifest.getStreams().stream()
              .anyMatch(m -> m.getGroupId().equals("org.jboss.xnio")
                && m.getArtifactId().equals("xnio-nio")
                && m.getVersion().equals(XNIO_VERSION)));
            if (stream == null) {
                assertThat(channelManifest.getStreams().stream()).containsExactlyElementsOf(installedManifest.getStreams());
            } else {
                assertThat(channelManifest.getStreams()).contains(stream);
                final Predicate<Stream> filter = s -> !s.getGroupId().equals(stream.getGroupId()) || !s.getArtifactId().equals(stream.getArtifactId());
                assertThat(channelManifest.getStreams().stream().filter(filter).collect(Collectors.toList()))
                  .containsExactlyElementsOf(installedManifest.getStreams().stream().filter(filter).collect(Collectors.toList()));
            }

            // check prosperoConfig
            assertEquals(1, prosperoConfig.getChannels().size());
            Channel channel = prosperoConfig.getChannels().get(0);
            assertEquals("test-channel-0", channel.getName());
            assertThat(channel.getRepositories())
              .map(Repository::getId).containsExactly("maven-central", "nexus", "maven-redhat-ga");

            // check provisionedConfig
            assertTrue(provisionedConfig.hasFeaturePackDeps());
            assertEquals(1, provisionedConfig.getFeaturePackDeps().size());
            FeaturePackConfig featurePackConfig = provisionedConfig.getFeaturePackDeps().iterator().next();
            assertEquals("org.wildfly.core:wildfly-core-galleon-pack::zip", featurePackConfig.getLocation().getProducerName());
        }
    }

    @Test
    public void testRestore() throws Exception {
        ChannelManifest installedManifest = install(targetDir, MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19));
        new InstallationExportAction(targetDir).export(exportPath);
        ExecutionUtils.prosperoExecution(CliConstants.Commands.CLONE, CliConstants.Commands.RESTORE,
            CliConstants.DIR, importDir.toString(),
            CliConstants.ARG_PATH, exportPath.toString()
          )
          .execute()
          .assertReturnCode(ReturnCodes.SUCCESS);
        checkMetaData(InstallationMetadata.loadInstallation(importDir), null, installedManifest);
    }

    @Test
    public void testRestoreOverrideRepositories() throws Exception {
        ChannelManifest installedManifest = install(targetDir, MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19));
        final Stream stream = new Stream("org.wildfly.core", "wildfly-cli", UPGRADE_VERSION);
        ChannelManifest manifest = ManifestYamlSupport.parse(ProsperoMetadataUtils.manifestPath(targetDir).toFile());
        manifest.findStreamFor(stream.getGroupId(), stream.getArtifactId()).ifPresent(s -> {
            manifest.getStreams().remove(s);
            manifest.getStreams().add(stream);
        });
        ProsperoMetadataUtils.writeManifest(ProsperoMetadataUtils.manifestPath(targetDir), manifest);
        new InstallationExportAction(targetDir).export(exportPath);
        ExecutionUtils.prosperoExecution(CliConstants.Commands.CLONE, CliConstants.Commands.RESTORE,
            CliConstants.DIR, importDir.toString(),
            CliConstants.ARG_PATH, exportPath.toString(),
            CliConstants.NO_LOCAL_MAVEN_CACHE,
            // mockTemporaryRepo contains org.wildfly.core:${UPGRADE_VERSION}
            CliConstants.REPOSITORIES, mockTemporaryRepo(false).toString()
          )
          .execute()
          .assertReturnCode(ReturnCodes.SUCCESS);
        checkMetaData(InstallationMetadata.loadInstallation(importDir), stream, installedManifest);
    }

}
