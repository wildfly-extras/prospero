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

package org.wildfly.prospero.cli.commands.channel;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.assertj.core.groups.Tuple.*;
import static org.jboss.galleon.util.PropertyUtils.isWindows;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.wildfly.prospero.cli.commands.channel.ChannelInitializeCommand.CUSTOM_CHANNELS_GROUP_ID;
import static org.wildfly.prospero.cli.commands.channel.ChannelInitializeCommand.DEFAULT_CUSTOMIZATION_REPOSITORY;
import static org.wildfly.prospero.cli.commands.channel.ChannelInitializeCommand.CUSTOMIZATION_REPO_ID;

public class ChannelInitializeCommandTest extends AbstractConsoleTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path installationDir;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        installationDir = tempFolder.newFolder().toPath();

        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir, "org.wildfly.core:core-feature-pack");
    }

    @Test
    public void currentDirNotValidInstallation() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL);

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(Paths.get(".").toAbsolutePath().toAbsolutePath())
                .getMessage()));
    }

    @Test
    public void initCustomChannelAddsChannelAndRepo() throws Exception {
        final String customRepoUrl = tempFolder.newFolder().toURI().toURL().toString();
        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CHANNEL_MANIFEST, "org.test:custom-channel",
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, customRepoUrl);
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        assertThat(actualChannels())
                .map(c -> c.getManifestCoordinate().getMaven())
                .contains(
                    new MavenCoordinate("org.test", "custom-channel", null)
                );
        assertThat(actualRepositories())
                .map(r-> tuple(r.getId(), r.getUrl()))
                .contains(
                        tuple(CUSTOMIZATION_REPO_ID, customRepoUrl)
                );
    }

    @Test
    public void customRepositoryAlreadyExists() throws Exception {
        final String customRepoUrl = tempFolder.newFolder().toURI().toURL().toString();
        final InstallationMetadata im = InstallationMetadata.loadInstallation(installationDir);
        Channel channel = new Channel("Customization channel", null, null,
                List.of(new Repository(CUSTOMIZATION_REPO_ID, customRepoUrl)),
                new ChannelManifestCoordinate("org.test", "test"),
                null, null);
        final ProsperoConfig prosperoConfig = im.getProsperoConfig();
        prosperoConfig.getChannels().add(channel);
        im.updateProsperoConfig(prosperoConfig);

        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CHANNEL_MANIFEST, "org.test:custom-channel",
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, customRepoUrl);

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.customizationRepoExist(CUSTOMIZATION_REPO_ID)));
        assertThat(actualChannels())
                .map(c -> c.getManifestCoordinate().getMaven())
                .doesNotContain(
                    new MavenCoordinate("org.test", "custom-channel", null)
                );
    }

    @Test
    public void illegalChannelName() throws Exception {
        final String customRepoUrl = tempFolder.newFolder().toURI().toURL().toString();

        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CHANNEL_MANIFEST, "org.test.custom-channel",
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, customRepoUrl);

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.illegalChannel("org.test.custom-channel")));
        assertThat(actualRepositories())
                .map(r-> tuple(r.getId(), r.getUrl()))
                .doesNotContain(
                        tuple(CUSTOMIZATION_REPO_ID, customRepoUrl)
                );
    }

    @Test
    public void localCustomRepoDirIsCreated() throws Exception {
        final Path base = tempFolder.newFolder().toPath();
        final Path repositoryPath = base.resolve("test-dir").resolve("repository");
        final String customRepoUrl = repositoryPath.toUri().toURL().toString();

        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CHANNEL_MANIFEST, "org.test:custom-channel",
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, customRepoUrl);

        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        assertTrue(Files.exists(repositoryPath));
    }

    @Test
    public void localCustomRepoDirIsWriteProtected() throws Exception {
        Assume.assumeFalse("Direcotry cannot be readonly on Windows", isWindows());

        final Path base = tempFolder.newFolder().toPath();
        try {
            base.toFile().setWritable(false);
            final Path repositoryPath = base.resolve("test-dir").resolve("repository");
            final String customRepoUrl = repositoryPath.toUri().toURL().toString();

            int exitCode = commandLine.execute(
                    CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                    CliConstants.DIR, installationDir.toString(),
                    CliConstants.CHANNEL_MANIFEST, "org.test:custom-channel",
                    CliConstants.CUSTOMIZATION_REPOSITORY_URL, customRepoUrl);

            Assert.assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
            assertThat(actualRepositories())
                    .map(r-> tuple(r.getId(), r.getUrl()))
                    .doesNotContain(
                            tuple(CUSTOMIZATION_REPO_ID, customRepoUrl)
                    );
            assertThat(actualChannels())
                    .map(c -> c.getManifestCoordinate().getMaven())
                    .doesNotContain(
                        new MavenCoordinate("org.test", "custom-channel", null)
                    );
        } finally {
            base.toFile().setWritable(true);
        }
    }

    @Test
    public void localCustomRepoPointsToFile() throws Exception {
        final Path base = tempFolder.newFolder().toPath();
        final Path repositoryPath = base.resolve("test.txt");
        Files.createFile(repositoryPath);
        final String customRepoUrl = repositoryPath.toUri().toURL().toString();

        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CHANNEL_MANIFEST, "org.test:custom-channel",
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, customRepoUrl);

        Assert.assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
        assertThat(actualRepositories())
                .map(r-> tuple(r.getId(), r.getUrl()))
                .doesNotContain(
                        tuple(CUSTOMIZATION_REPO_ID, customRepoUrl)
                );
        assertThat(actualChannels())
                .map(c -> c.getManifestCoordinate().getMaven())
                .doesNotContain(
                    new MavenCoordinate("org.test", "custom-channel", null)
                );
    }

    @Test
    public void initDefaultRepositoryIfNoUrlProvided() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CHANNEL_MANIFEST, "org.test:custom-channel");

        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        assertThat(actualRepositories())
                .map(r-> tuple(r.getId(), r.getUrl()))
                .contains(
                    tuple(CUSTOMIZATION_REPO_ID,
                        installationDir.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(DEFAULT_CUSTOMIZATION_REPOSITORY).toUri().toURL().toString())
                );
        assertThat(actualChannels())
                .map(c -> c.getManifestCoordinate().getMaven())
                .contains(
                    new MavenCoordinate("org.test", "custom-channel", null)
                );
    }

    @Test
    public void initDefaultChannelIfNoChannelNameProvided() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString());

        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        final Matcher matcher = Pattern.compile("Registering custom channel `(.*)`").matcher(getStandardOutput());
        assertTrue(matcher.find());
        final String channelName = matcher.group(1);
        assertNotNull(channelName);

        assertThat(actualRepositories())
                .map(r-> tuple(r.getId(), r.getUrl()))
                .contains(
                    tuple(CUSTOMIZATION_REPO_ID,
                        installationDir.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(DEFAULT_CUSTOMIZATION_REPOSITORY).toUri().toURL().toString())
        );
        assertThat(actualChannels())
                .map(c -> c.getManifestCoordinate().getMaven())
                .contains(
                        new MavenCoordinate(channelName.split(":")[0], channelName.split(":")[1], null)
                );
    }

    @Test
    public void onlyOneDefaultChannelCanBeCreated() throws Exception {
        Channel channel = new Channel("base", null, null,
                List.of(new Repository("test", "http://test.org/repo")),
                new ChannelManifestCoordinate(CUSTOM_CHANNELS_GROUP_ID, "existing"),
                null, null);

        final Path configFilePath = ProsperoMetadataUtils.configurationPath(installationDir);
        ProsperoMetadataUtils.writeChannelsConfiguration(configFilePath, List.of(channel));
        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, "http://test.repo2",
                CliConstants.DIR, installationDir.toString());

        Assert.assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);

        assertThat(actualRepositories())
                .map(r-> tuple(r.getId(), r.getUrl()))
                .containsOnly(
                        tuple("test", "http://test.org/repo")
                );
        assertThat(actualChannels())
                .map(c -> c.getManifestCoordinate().getMaven())
                .containsOnly(
                    new MavenCoordinate(CUSTOM_CHANNELS_GROUP_ID, "existing", null)
                );
    }

    private List<Channel> actualChannels() throws MetadataException {
        return InstallationMetadata.loadInstallation(installationDir).getProsperoConfig().getChannels();
    }

    private List<Repository> actualRepositories() throws MetadataException {
        return InstallationMetadata.loadInstallation(installationDir).getProsperoConfig()
                .getChannels().stream()
                .flatMap((Channel channel) -> channel.getRepositories().stream())
                .collect(Collectors.toList());
    }
}