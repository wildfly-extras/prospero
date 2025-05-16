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

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.jboss.galleon.ProvisioningException;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.it.utils.TestProperties;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.test.TestLocalRepository;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenerateTest {

    private static final String PRODUCT;
    private static final String VERSION;
    protected static final String BASE_DIST_URL;
    protected static final String CORE_VERSION;// = "20.0.1.Final";

    static {
        try {
            final Properties properties = new Properties();
            properties.load(GenerateTest.class.getClassLoader().getResourceAsStream("properties-from-pom.properties"));
            PRODUCT = (String) properties.get("prospero.test.generate.server.profile");
            VERSION = (String) properties.get("prospero.test.generate.server_dist.version");
            BASE_DIST_URL = (String) properties.get("prospero.test.generate.server_dist.url");
            CORE_VERSION = (String) properties.get("prospero.test.generate.server_core.version");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path targetDir;

    @Before
    public void setUp() throws IOException {
        targetDir = tempDir.newFolder().toPath();
    }

    @Test
    public void testInstallWithProvisionConfig() throws Exception {
        // prepare a server by downloading and unzipping it
        Path serverDir = downloadAndUnzipServer();

        // generate the metadata using prospero CLI
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.SUBSCRIBE,
                        CliConstants.PRODUCT, PRODUCT,
                        CliConstants.VERSION, VERSION,
                        CliConstants.Y,
                        CliConstants.REPOSITORIES, String.join(",", TestProperties.TEST_REPO_URLS),
                        CliConstants.DIR, serverDir.toString())
                .withTimeLimit(20, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // check the metadata existence
        Path manifestPath = ProsperoMetadataUtils.manifestPath(serverDir);
        Path configurationPath = ProsperoMetadataUtils.configurationPath(serverDir);
        assertTrue(Files.exists(manifestPath));
        ChannelManifest manifest = ManifestYamlSupport.parse(manifestPath.toFile());
        assertEquals(new Stream("org.wildfly.core", "wildfly-server", CORE_VERSION),
                manifest.getStreams().stream().filter(s -> s.getGroupId().equals("org.wildfly.core")
                        && s.getArtifactId().equals("wildfly-server")).findFirst().orElse(null));
        assertTrue(Files.exists(configurationPath));
        ProsperoConfig prosperoConfig = ProsperoConfig.readConfig(serverDir.resolve(ProsperoMetadataUtils.METADATA_DIR));
        Assert.assertFalse(prosperoConfig.getChannels().isEmpty());

        final MavenCoordinate channelMavenCoord = currentServerChannelCoord(serverDir);

        final TestLocalRepository testLocalRepository = setUpTestRepository(manifest, channelMavenCoord);

        // run upgrade
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PERFORM,
            CliConstants.Y,
            CliConstants.DIR, serverDir.toString(),
            CliConstants.REPOSITORIES, String.join(",", TestProperties.TEST_REPO_URLS) + "," + testLocalRepository.getUri().toString())
          .withTimeLimit(20, TimeUnit.MINUTES)
          .execute()
          .assertReturnCode(ReturnCodes.SUCCESS);

        // check the vertx-core stream in manifest
        manifest = ManifestYamlSupport.parse(manifestPath.toFile());
        final String ver = manifest.getStreams().stream()
                .filter(s -> s.getGroupId().equals("org.wildfly.core") && s.getArtifactId().equals("wildfly-server"))
                .map(Stream::getVersion).findFirst().get();
        assertEquals(CORE_VERSION + ".SP1", ver);
    }

    private static MavenCoordinate currentServerChannelCoord(Path serverDir) throws MalformedURLException {
        final Channel currentChannel = ChannelMapper.from(ProsperoMetadataUtils.configurationPath(serverDir).toUri().toURL());
        return currentChannel.getManifestCoordinate().getMaven();
    }

    @NotNull
    private TestLocalRepository setUpTestRepository(ChannelManifest manifest, MavenCoordinate channelMavenCoord) throws ProvisioningException, IOException, DeploymentException, ArtifactResolutionException {
        // create local repository so that we can control exactly the updates
        final TestLocalRepository testLocalRepository = new TestLocalRepository(tempDir.newFolder("test-repo").toPath(), TestProperties.TEST_REPO_URLS.stream().map(s -> {
            try {
                return URI.create(s).toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList()));
        testLocalRepository.deployMockUpdate("org.wildfly.core", "wildfly-server", CORE_VERSION, ".SP1");

        // modify the wildfly-server entry in the manifest
        ChannelManifest.Builder cm = new ChannelManifest.Builder()
                .setName(manifest.getName());
        manifest.getStreams().stream().map(s -> {
            if (s.getGroupId().equals("org.wildfly.core")
                    && s.getArtifactId().equals("wildfly-server")) {
                return new Stream("org.wildfly.core", "wildfly-server", CORE_VERSION + ".SP1");
            } else {
                return s;
            }
        }).forEach(cm::addStreams);
        // and deploy the modified manifest
        testLocalRepository.deploy(new DefaultArtifact(
                        channelMavenCoord.getGroupId(),
                        channelMavenCoord.getArtifactId(),
                        ChannelManifest.CLASSIFIER,
                        ChannelManifest.EXTENSION,
                        "9999.99.99"), // some very big version to ensure this update wins
                cm.build());
        return testLocalRepository;
    }

    private Path downloadAndUnzipServer() throws IOException {
        final URL downloadUrl = URI.create(BASE_DIST_URL).toURL();
        final InputStream inputStream = downloadUrl.openStream();
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(targetDir.resolve(entry.getName()));
                } else {
                    Files.copy(zis, targetDir.resolve(entry.getName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        // in case the distro contains installation metadata - erase them
        try (java.util.stream.Stream<Path> files = Files.list(targetDir)) {
            final Path serverDir = files.filter(Files::isDirectory).findFirst().orElseThrow();
            FileUtils.deleteDirectory(serverDir.resolve(ProsperoMetadataUtils.METADATA_DIR).toFile());
            return serverDir;
        }
    }
}
