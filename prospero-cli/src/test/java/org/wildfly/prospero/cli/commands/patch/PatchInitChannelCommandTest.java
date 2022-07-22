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

package org.wildfly.prospero.cli.commands.patch;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.wildfly.prospero.cli.commands.patch.PatchInitChannelCommand.CUSTOM_CHANNELS_GROUP_ID;
import static org.wildfly.prospero.cli.commands.patch.PatchInitChannelCommand.DEFAULT_CUSTOMIZATION_REPOSITORY;
import static org.wildfly.prospero.cli.commands.patch.PatchInitChannelCommand.PATCHES_REPO_ID;

public class PatchInitChannelCommandTest extends AbstractConsoleTest {

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
        int exitCode = commandLine.execute("patch", "init-channel");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(Paths.get(".").toAbsolutePath().toAbsolutePath())
                .getMessage()));
    }

    @Test
    public void initPatchChannelAddsChannelAndRepo() throws Exception {
        final String patchesRepoUrl = tempFolder.newFolder().toURI().toURL().toString();
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, CliConstants.PATCH_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.PATCH_CHANNEL_NAME, "org.test:patches",
                CliConstants.PATCH_REPOSITORY_URL, patchesRepoUrl);
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        assertThat(actualChannels()).contains(
                new ChannelRef("org.test:patches", null)
        );
        assertThat(actualRepositories()).contains(
                new RepositoryRef(PATCHES_REPO_ID, patchesRepoUrl)
        );
    }

    @Test
    public void patchRepositoryAlreadyExists() throws Exception {
        final String patchesRepoUrl = tempFolder.newFolder().toURI().toURL().toString();
        final InstallationMetadata im = new InstallationMetadata(installationDir);
        final ProsperoConfig prosperoConfig = im.getProsperoConfig();
        prosperoConfig.addRepository(new RepositoryRef(PATCHES_REPO_ID, patchesRepoUrl));
        im.updateProsperoConfig(prosperoConfig);

        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, CliConstants.PATCH_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.PATCH_CHANNEL_NAME, "org.test:patches",
                CliConstants.PATCH_REPOSITORY_URL, patchesRepoUrl);

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.patchesRepoExist(PATCHES_REPO_ID)));
        assertThat(actualChannels()).doesNotContain(
                new ChannelRef("org.test:patches", null)
        );
    }

    @Test
    public void illegalChannelName() throws Exception {
        final String patchesRepoUrl = tempFolder.newFolder().toURI().toURL().toString();

        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, CliConstants.PATCH_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.PATCH_CHANNEL_NAME, "org.test.patches",
                CliConstants.PATCH_REPOSITORY_URL, patchesRepoUrl);

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.illegalChannel("org.test.patches")));
        assertThat(actualRepositories()).doesNotContain(
                new RepositoryRef(PATCHES_REPO_ID, patchesRepoUrl)
        );
    }

    @Test
    public void localPatchRepoDirIsCreated() throws Exception {
        final Path base = tempFolder.newFolder().toPath();
        final Path repositoryPath = base.resolve("test-dir").resolve("repository");
        final String patchesRepoUrl = repositoryPath.toUri().toURL().toString();

        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, CliConstants.PATCH_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.PATCH_CHANNEL_NAME, "org.test:patches",
                CliConstants.PATCH_REPOSITORY_URL, patchesRepoUrl);

        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        assertTrue(Files.exists(repositoryPath));
    }

    @Test
    public void localPatchRepoDirIsWriteProtected() throws Exception {
        final Path base = tempFolder.newFolder().toPath();
        try {
            base.toFile().setWritable(false);
            final Path repositoryPath = base.resolve("test-dir").resolve("repository");
            final String patchesRepoUrl = repositoryPath.toUri().toURL().toString();

            int exitCode = commandLine.execute(
                    CliConstants.Commands.PATCH, CliConstants.PATCH_INIT_CHANNEL,
                    CliConstants.DIR, installationDir.toString(),
                    CliConstants.PATCH_CHANNEL_NAME, "org.test:patches",
                    CliConstants.PATCH_REPOSITORY_URL, patchesRepoUrl);

            Assert.assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
            assertThat(actualRepositories()).doesNotContain(
                    new RepositoryRef(PATCHES_REPO_ID, patchesRepoUrl)
            );
            assertThat(actualChannels()).doesNotContain(
                    new ChannelRef("org.test:patches", null)
            );
        } finally {
            base.toFile().setWritable(true);
        }
    }

    @Test
    public void localPatchRepoPointsToFile() throws Exception {
        final Path base = tempFolder.newFolder().toPath();
        final Path repositoryPath = base.resolve("test.txt");
        Files.createFile(repositoryPath);
        final String patchesRepoUrl = repositoryPath.toUri().toURL().toString();

        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, CliConstants.PATCH_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.PATCH_CHANNEL_NAME, "org.test:patches",
                CliConstants.PATCH_REPOSITORY_URL, patchesRepoUrl);

        Assert.assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
        assertThat(actualRepositories()).doesNotContain(
                new RepositoryRef(PATCHES_REPO_ID, patchesRepoUrl)
        );
        assertThat(actualChannels()).doesNotContain(
                new ChannelRef("org.test:patches", null)
        );
    }

    @Test
    public void initDefaultRepositoryIfNoUrlProvided() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, CliConstants.PATCH_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.PATCH_CHANNEL_NAME, "org.test:patches");

        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        assertThat(actualRepositories()).contains(
                new RepositoryRef(PATCHES_REPO_ID,
                        installationDir.resolve(InstallationMetadata.METADATA_DIR).resolve(DEFAULT_CUSTOMIZATION_REPOSITORY).toUri().toURL().toString())
        );
        assertThat(actualChannels()).contains(
                new ChannelRef("org.test:patches", null)
        );
    }

    @Test
    public void initDefaultChannelIfNoChannelNameProvided() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, CliConstants.PATCH_INIT_CHANNEL,
                CliConstants.DIR, installationDir.toString());

        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        final Matcher matcher = Pattern.compile("Registering custom channel `(.*)`").matcher(getStandardOutput());
        assertTrue(matcher.find());
        final String channelName = matcher.group(1);
        assertNotNull(channelName);

        assertThat(actualRepositories()).contains(
                new RepositoryRef(PATCHES_REPO_ID,
                        installationDir.resolve(InstallationMetadata.METADATA_DIR).resolve(DEFAULT_CUSTOMIZATION_REPOSITORY).toUri().toURL().toString())
        );
        assertThat(actualChannels()).contains(
                new ChannelRef(channelName, null)
        );
    }

    @Test
    public void onlyOneDefaultChannelCanBeCreated() throws Exception {
        new ProsperoConfig(Arrays.asList(new ChannelRef(CUSTOM_CHANNELS_GROUP_ID + ":existing", null)),
                Collections.emptyList())
                .writeConfig(installationDir.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME).toFile());
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, CliConstants.PATCH_INIT_CHANNEL,
                CliConstants.PATCH_REPOSITORY_URL, "http://test.repo2",
                CliConstants.DIR, installationDir.toString());

        Assert.assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);

        assertThat(actualRepositories()).isEmpty();
        assertThat(actualChannels()).containsOnly(
                new ChannelRef(CUSTOM_CHANNELS_GROUP_ID + ":existing", null)
        );
    }

    private List<ChannelRef> actualChannels() throws MetadataException {
        return new InstallationMetadata(installationDir).getProsperoConfig().getChannels();
    }

    private List<RepositoryRef> actualRepositories() throws MetadataException {
        return new InstallationMetadata(installationDir).getProsperoConfig().getRepositories();
    }
}