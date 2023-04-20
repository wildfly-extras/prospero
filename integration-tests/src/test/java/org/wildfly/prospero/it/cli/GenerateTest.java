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

package org.wildfly.prospero.it.cli;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.jboss.galleon.ProvisioningException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.KnownFeaturePacks;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.model.KnownFeaturePack;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GenerateTest {

    private static final String PRODUCT = "wildfly";
    private static final String VERSION = "28.0.0.Final";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path targetDir;

    @Before
    public void setUp() throws IOException {
        targetDir = tempDir.newFolder().toPath();
    }

    @Test
    public void testInstallWithProvisionConfig() throws Exception {
        // prepare a wildfly-28.0.0.Final server by downloading and unzipping it
        downloadAndUnzipServer();

        Path serverDir = targetDir.resolve(PRODUCT + "-" + VERSION);
        // generate the metadata using prospero CLI
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.GENERATE,
                        CliConstants.PRODUCT, PRODUCT,
                        CliConstants.VERSION, VERSION,
                        CliConstants.Y,
                        CliConstants.DIR, serverDir.toString())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // check the metadata existence
        Path manifestPath = ProsperoMetadataUtils.manifestPath(serverDir);
        Path configurationPath = ProsperoMetadataUtils.configurationPath(serverDir);
        Assert.assertTrue(Files.exists(manifestPath));
        ChannelManifest manifest = ManifestYamlSupport.parse(manifestPath.toFile());
        Assert.assertTrue(manifest.getStreams().contains(new Stream("org.wildfly.core", "wildfly-server", "20.0.1.Final")));
        Assert.assertTrue(Files.exists(configurationPath));
        ProsperoConfig prosperoConfig = ProsperoConfig.readConfig(serverDir.resolve(ProsperoMetadataUtils.METADATA_DIR));
        Assert.assertTrue(prosperoConfig.getChannels().size() > 0);
        Path manifestPathCopy = serverDir.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("manifest-" + PRODUCT + "-" + VERSION + ".yaml");
        Assert.assertTrue(Files.exists(manifestPathCopy));
        Assert.assertTrue(prosperoConfig.getChannels().stream()
          .anyMatch(c -> {
              try {
                  return c.getManifestCoordinate() != null && c.getManifestCoordinate().getUrl() != null &&
                    c.getManifestCoordinate().getUrl().equals(manifestPathCopy.toUri().toURL());
              } catch (MalformedURLException e) {
                  throw new RuntimeException(e);
              }
          }));

        // update stream in metadata, test with upgrade vertx-core from 4.3.4 to 4.3.6
        final Artifact upgrade = new DefaultArtifact("io.vertx", "vertx-core", "jar", "4.3.6");
        final List<Stream> updatedStreams = manifest.getStreams().stream()
          .map(s -> {
              if (s.getGroupId().equals(upgrade.getGroupId()) && s.getArtifactId().equals(upgrade.getArtifactId())) {
                  return new Stream(upgrade.getGroupId(), upgrade.getArtifactId(), upgrade.getVersion());
              } else {
                  return s;
              }
          }).collect(Collectors.toList());
        Files.writeString(manifestPathCopy, ChannelManifestMapper.toYaml(new ChannelManifest(manifest.getName(), manifest.getId(), manifest.getDescription(), updatedStreams)));

        // run upgrade
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PERFORM,
            CliConstants.Y,
            CliConstants.DIR, serverDir.toString())
          .execute()
          .assertReturnCode(ReturnCodes.SUCCESS);

        // check the vertx-core stream in manifest
        manifest = ManifestYamlSupport.parse(manifestPath.toFile());
        Assert.assertTrue(manifest.getStreams().contains(new Stream("io.vertx", "vertx-core", "4.3.6")));
    }

    private void downloadAndUnzipServer() throws ProvisioningException, ArtifactResolutionException, IOException {
        KnownFeaturePack knownFeaturePack = KnownFeaturePacks.getByName(PRODUCT);
        ProsperoConfig prosperoConfig = new ProsperoConfig(knownFeaturePack.getChannels());
        final MavenSessionManager mavenSessionManager = new MavenSessionManager();
        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system);
        File serverZip = system.resolveArtifact(session, new ArtifactRequest()
            .setRepositories(new ArrayList<>(prosperoConfig.listAllRepositories()))
          .setArtifact(new DefaultArtifact("org.wildfly", "wildfly-dist", "zip", VERSION)))
          .getArtifact().getFile();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(serverZip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(targetDir.resolve(entry.getName()));
                } else {
                    Files.copy(zis, targetDir.resolve(entry.getName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
