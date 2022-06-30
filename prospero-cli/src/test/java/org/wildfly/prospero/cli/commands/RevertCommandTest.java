package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistory;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RevertCommandTest extends AbstractConsoleTest {

    @Mock
    private InstallationHistory historyAction;

    @Captor
    private ArgumentCaptor<MavenSessionManager> mavenSessionManager;

    @Override
    protected ActionFactory createActionFactory() {
        return new ActionFactory() {
            @Override
            public InstallationHistory history(Path targetPath, Console console) {
                return historyAction;
            }
        };
    }

    @Test
    public void requireDirArgument() {
        int exitCode = commandLine.execute(CliConstants.REVERT);

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format(
                "Missing required options: '%s=<directory>', '%s=<revision>'", CliConstants.DIR, CliConstants.REVISION)));
    }

    @Test
    public void requireRevisionArgument() {
        int exitCode = commandLine.execute(CliConstants.REVERT, CliConstants.DIR, "test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format(
                "Missing required option: '%s=<revision>'", CliConstants.REVISION)));
    }

    @Test
    public void callRevertOpertation() throws Exception {
        int exitCode = commandLine.execute(CliConstants.REVERT, CliConstants.DIR, "test", CliConstants.REVISION, "abcd");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(historyAction).rollback(eq(new SavedState("abcd")), any());
    }

    @Test
    public void offlineModeRequiresLocalRepoOption() {
        int exitCode = commandLine.execute(CliConstants.REVERT, CliConstants.DIR, "test", CliConstants.REVISION, "abcd",
                CliConstants.OFFLINE);

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.offlineModeRequiresLocalRepo()));
    }

    @Test
    public void useOfflineMavenSessionManagerIfOfflineSet() throws Exception {
        int exitCode = commandLine.execute(CliConstants.REVERT, CliConstants.DIR, "test", CliConstants.REVISION, "abcd",
                CliConstants.OFFLINE, CliConstants.LOCAL_REPO, "local-repo");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(historyAction).rollback(eq(new SavedState("abcd")), mavenSessionManager.capture());
        assertTrue(mavenSessionManager.getValue().isOffline());
    }

    @Test
    public void useLocalMavenRepoIfParameterSet() throws Exception {
        int exitCode = commandLine.execute(CliConstants.REVERT, CliConstants.DIR, "test", CliConstants.REVISION, "abcd",
                CliConstants.LOCAL_REPO, "local-repo");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(historyAction).rollback(eq(new SavedState("abcd")), mavenSessionManager.capture());
        assertEquals(Paths.get("local-repo").toAbsolutePath(), mavenSessionManager.getValue().getProvisioningRepo());
    }
}