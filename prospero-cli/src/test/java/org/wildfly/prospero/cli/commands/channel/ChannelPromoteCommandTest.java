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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.actions.PromoteArtifactBundleAction;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wildfly.prospero.cli.commands.CliConstants.Commands.CUSTOMIZATION_PROMOTE;
import static org.wildfly.prospero.cli.commands.channel.ChannelInitializeCommand.CUSTOM_CHANNELS_GROUP_ID;
import static org.wildfly.prospero.cli.commands.channel.ChannelInitializeCommand.CUSTOMIZATION_REPO_ID;

@RunWith(MockitoJUnitRunner.class)
public class ChannelPromoteCommandTest extends AbstractConsoleTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    private ActionFactory actionFactory;

    @Mock
    private MetadataAction metadataAction;

    @Mock
    private PromoteArtifactBundleAction promoter;

    @Override
    protected ActionFactory createActionFactory() {
        return actionFactory;
    }

    @Test
    public void callPromoteAction() throws Exception {
        when(actionFactory.promoter(any())).thenReturn(promoter);
        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CUSTOMIZATION_PROMOTE,
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, "file:///test/test",
                CliConstants.CUSTOMIZATION_ARCHIVE, "test/archive.zip",
                CliConstants.CUSTOMIZATION_CHANNEL_NAME, "org.test:custom-channel");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(promoter).promote(Paths.get("test/archive.zip").toAbsolutePath(), new URL("file:///test/test"),
                ChannelRef.fromString("org.test:custom-channel"));
    }

    @Test
    public void missingRepositoryUrl() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CUSTOMIZATION_PROMOTE,
                CliConstants.CUSTOMIZATION_ARCHIVE, "test/archive.zip",
                CliConstants.CUSTOMIZATION_CHANNEL_NAME, "org.test:custom-channel");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains("Unable to determine custom channel and repository"));
    }

    @Test
    public void missingArchive() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CUSTOMIZATION_PROMOTE,
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, "file:///test/test",
                CliConstants.CUSTOMIZATION_CHANNEL_NAME, "org.test:custom-channel");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s", CliConstants.CUSTOMIZATION_ARCHIVE)));
    }

    @Test
    public void missingChannelName() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CUSTOMIZATION_PROMOTE,
                CliConstants.CUSTOMIZATION_ARCHIVE, "test/archive.zip",
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, "file:///test/test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains("Unable to determine custom channel and repository"));
    }

    @Test
    public void illegalChannelName() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CUSTOMIZATION_PROMOTE,
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, "file:///test/test",
                CliConstants.CUSTOMIZATION_ARCHIVE, "test/archive.zip",
                CliConstants.CUSTOMIZATION_CHANNEL_NAME, "wrongchannelsyntax");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.wrongChannelCoordinateFormat()));
    }

    @Test
    public void ifDirIsPresentReadChannelAndRepo() throws Exception {
        Path installationDir = tempFolder.newFolder().toPath();
        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir, "org.wildfly.core:core-feature-pack");

        when(actionFactory.promoter(any())).thenReturn(promoter);
        when(actionFactory.metadataActions(any())).thenReturn(metadataAction);
        when(metadataAction.getChannels()).thenReturn(Arrays.asList(new ChannelRef(ChannelRef.Type.GAV, CUSTOM_CHANNELS_GROUP_ID + ":test1", null, null, null)));
        when(metadataAction.getRepositories()).thenReturn(Arrays.asList(new RepositoryRef(CUSTOMIZATION_REPO_ID, "file:///test/test")));

        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CUSTOMIZATION_PROMOTE,
                CliConstants.CUSTOMIZATION_ARCHIVE, "test/archive.zip",
                CliConstants.DIR, installationDir.toString()
                );
        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(promoter).promote(Paths.get("test/archive.zip").toAbsolutePath(), new URL("file:///test/test"),
                ChannelRef.fromString(CUSTOM_CHANNELS_GROUP_ID + ":test1"));
    }

    @Test
    public void ifParametersPresentSkipInstallationSettings() throws Exception {
        Path installationDir = tempFolder.newFolder().toPath();
        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir, "org.wildfly.core:core-feature-pack");

        when(actionFactory.promoter(any())).thenReturn(promoter);

        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CUSTOMIZATION_PROMOTE,
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, "http://test.repo",
                CliConstants.CUSTOMIZATION_CHANNEL_NAME, "org.custom:test",
                CliConstants.CUSTOMIZATION_ARCHIVE, "test/archive.zip",
                CliConstants.DIR, installationDir.toString()
        );
        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(promoter).promote(Paths.get("test/archive.zip").toAbsolutePath(), new URL("http://test.repo"),
                ChannelRef.fromString("org.custom:test"));
    }

    @Test
    public void ifChannelMissingReadFromInstallationSettings() throws Exception {
        Path installationDir = tempFolder.newFolder().toPath();
        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir, "org.wildfly.core:core-feature-pack");

        when(actionFactory.promoter(any())).thenReturn(promoter);
        when(actionFactory.metadataActions(any())).thenReturn(metadataAction);
        when(metadataAction.getChannels()).thenReturn(Arrays.asList(new ChannelRef(ChannelRef.Type.GAV, CUSTOM_CHANNELS_GROUP_ID + ":test1", null, null, null)));

        int exitCode = commandLine.execute(
                CliConstants.Commands.CHANNEL, CUSTOMIZATION_PROMOTE,
                CliConstants.CUSTOMIZATION_REPOSITORY_URL, "http://test.repo",
                CliConstants.CUSTOMIZATION_ARCHIVE, "test/archive.zip",
                CliConstants.DIR, installationDir.toString()
        );
        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(promoter).promote(Paths.get("test/archive.zip").toAbsolutePath(), new URL("http://test.repo"),
                ChannelRef.fromString(CUSTOM_CHANNELS_GROUP_ID + ":test1"));
    }
}