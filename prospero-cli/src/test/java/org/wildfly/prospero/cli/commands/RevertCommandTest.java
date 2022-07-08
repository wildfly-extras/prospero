package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RevertCommandTest extends AbstractMavenCommandTest {

    @Mock
    private InstallationHistoryAction historyAction;

    @Captor
    private ArgumentCaptor<MavenSessionManager> mavenSessionManager;

    @Override
    protected ActionFactory createActionFactory() {
        return new ActionFactory() {
            @Override
            public InstallationHistoryAction history(Path targetPath, Console console) {
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
    public void useOfflineMavenSessionManagerIfOfflineSet() throws Exception {
        int exitCode = commandLine.execute(CliConstants.REVERT, CliConstants.DIR, "test", CliConstants.REVISION, "abcd",
                CliConstants.OFFLINE, CliConstants.LOCAL_REPO, "local-repo");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(historyAction).rollback(eq(new SavedState("abcd")), mavenSessionManager.capture());
        assertTrue(mavenSessionManager.getValue().isOffline());
    }

    @Override
    protected MavenSessionManager getCapturedSessionManager() throws Exception {
        verify(historyAction).rollback(eq(new SavedState("abcd")), mavenSessionManager.capture());
        return mavenSessionManager.getValue();
    }

    @Override
    protected String[] getDefaultArguments() {
        return new String[]{CliConstants.REVERT, CliConstants.DIR, "test", CliConstants.REVISION, "abcd"};
    }
}