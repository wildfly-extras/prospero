package org.wildfly.prospero.cli.commands.patch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.PromotePatchAction;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.model.ChannelRef;

import java.net.URL;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PatchPromoteCommandTest extends AbstractConsoleTest {

    @Mock
    private ActionFactory actionFactory;

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
                "--archive", "test/archive.zip",
                CliConstants.PATCH_CHANNEL_NAME, "org.test:patch-channel");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(promoter).promote(Paths.get("test/archive.zip").toAbsolutePath(), new URL("file:///test/test"),
                ChannelRef.fromString("org.test:patch-channel"));
    }

    @Test
    public void missingRepositoryUrl() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, "promote",
                "--archive", "test/archive.zip",
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
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s", "--archive")));
    }

    @Test
    public void missingChannelName() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, "promote",
                "--archive", "test/archive.zip",
                CliConstants.PATCH_REPOSITORY_URL, "file:///test/test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s", CliConstants.PATCH_CHANNEL_NAME)));
    }

    @Test
    public void illegalChannelName() throws Exception {
        int exitCode = commandLine.execute(
                CliConstants.Commands.PATCH, "promote",
                CliConstants.PATCH_REPOSITORY_URL, "file:///test/test",
                "--archive", "test/archive.zip",
                CliConstants.PATCH_CHANNEL_NAME, "wrongchannelsyntax");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.wrongChannelCoordinateFormat()));
    }
}