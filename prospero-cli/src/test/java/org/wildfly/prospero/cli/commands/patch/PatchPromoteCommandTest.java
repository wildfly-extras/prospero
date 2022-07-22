package org.wildfly.prospero.cli.commands.patch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.actions.PromotePatchAction;
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
import static org.wildfly.prospero.cli.commands.patch.PatchInitChannelCommand.CUSTOM_CHANNELS_GROUP_ID;
import static org.wildfly.prospero.cli.commands.patch.PatchInitChannelCommand.PATCHES_REPO_ID;

@RunWith(MockitoJUnitRunner.class)
public class PatchPromoteCommandTest extends AbstractConsoleTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    private ActionFactory actionFactory;

    @Mock
    private MetadataAction metadataAction;

    @Mock
    private PromotePatchAction promoter;

    @Override
    protected ActionFactory createActionFactory() {
        return actionFactory;
    }

    @Test
    public void callPromoteAction() throws Exception {
        when(actionFactory.promoter(any())).thenReturn(promoter);
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, "promote",
                CliConstants.PATCH_REPOSITORY_URL, "file:///test/test",
                CliConstants.PATCH_ARCHIVE, "test/archive.zip",
                CliConstants.PATCH_CHANNEL_NAME, "org.test:patch-channel");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(promoter).promote(Paths.get("test/archive.zip").toAbsolutePath(), new URL("file:///test/test"),
                ChannelRef.fromString("org.test:patch-channel"));
    }

    @Test
    public void missingRepositoryUrl() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, "promote",
                CliConstants.PATCH_ARCHIVE, "test/archive.zip",
                CliConstants.PATCH_CHANNEL_NAME, "org.test:patch-channel");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s", CliConstants.PATCH_REPOSITORY_URL)));
    }

    @Test
    public void missingArchive() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, "promote",
                CliConstants.PATCH_REPOSITORY_URL, "file:///test/test",
                CliConstants.PATCH_CHANNEL_NAME, "org.test:patch-channel");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s", CliConstants.PATCH_ARCHIVE)));
    }

    @Test
    public void missingChannelName() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, "promote",
                CliConstants.PATCH_ARCHIVE, "test/archive.zip",
                CliConstants.PATCH_REPOSITORY_URL, "file:///test/test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s", CliConstants.PATCH_CHANNEL_NAME)));
    }

    @Test
    public void illegalChannelName() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, "promote",
                CliConstants.PATCH_REPOSITORY_URL, "file:///test/test",
                CliConstants.PATCH_ARCHIVE, "test/archive.zip",
                CliConstants.PATCH_CHANNEL_NAME, "wrongchannelsyntax");

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
        when(metadataAction.getChannels()).thenReturn(Arrays.asList(new ChannelRef(CUSTOM_CHANNELS_GROUP_ID + ":test1", null)));
        when(metadataAction.getRepositories()).thenReturn(Arrays.asList(new RepositoryRef(PATCHES_REPO_ID, "file:///test/test")));

        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, "promote",
                CliConstants.PATCH_ARCHIVE, "test/archive.zip",
                CliConstants.DIR, installationDir.toString()
                );
        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(promoter).promote(Paths.get("test/archive.zip").toAbsolutePath(), new URL("file:///test/test"),
                ChannelRef.fromString(CUSTOM_CHANNELS_GROUP_ID + ":test1"));
    }
}