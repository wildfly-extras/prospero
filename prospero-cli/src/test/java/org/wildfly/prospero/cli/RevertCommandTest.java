package org.wildfly.prospero.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistory;
import org.wildfly.prospero.api.SavedState;
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
        int exitCode = commandLine.execute("revert");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains("Missing required options: '--dir=<directory>', '--revision=<revision>'"));
    }

    @Test
    public void requireRevisionArgument() {
        int exitCode = commandLine.execute("revert", "--dir", "test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains("Missing required option: '--revision=<revision>'"));
    }

    @Test
    public void callRevertOpertation() throws Exception {
        int exitCode = commandLine.execute("revert", "--dir", "test", "--revision", "abcd");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(historyAction).rollback(eq(new SavedState("abcd")), any());
    }

    @Test
    public void offlineModeRequiresLocalRepoOption() {
        int exitCode = commandLine.execute("revert", "--dir", "test", "--revision", "abcd", "--offline");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(Messages.offlineModeRequiresLocalRepo()));
    }

    @Test
    public void useOfflineMavenSessionManagerIfOfflineSet() throws Exception {
        int exitCode = commandLine.execute("revert", "--dir", "test", "--revision", "abcd", "--offline",
                "--local-repo", "local-repo");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(historyAction).rollback(eq(new SavedState("abcd")), mavenSessionManager.capture());
        assertTrue(mavenSessionManager.getValue().isOffline());
    }

    @Test
    public void useLocalMavenRepoIfParameterSet() throws Exception {
        int exitCode = commandLine.execute("revert", "--dir", "test", "--revision", "abcd",
                "--local-repo", "local-repo");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(historyAction).rollback(eq(new SavedState("abcd")), mavenSessionManager.capture());
        assertEquals(Paths.get("local-repo").toAbsolutePath(), mavenSessionManager.getValue().getProvisioningRepo());
    }
}