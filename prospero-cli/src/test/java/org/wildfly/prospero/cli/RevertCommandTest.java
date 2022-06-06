package org.wildfly.prospero.cli;

import org.wildfly.prospero.actions.InstallationHistory;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RevertCommandTest {

    @Mock
    private InstallationHistory historyAction;

    @Mock
    private CliMain.ActionFactory actionFactory;

    @Captor
    private ArgumentCaptor<MavenSessionManager> mavenSessionManager;

    private RevertCommand revertCommand;

    @Before
    public void setUp() {
        revertCommand = new RevertCommand(actionFactory);
    }

    @Test(expected = ArgumentParsingException.class)
    public void requireDirArgument() throws Exception {
        Map<String, String> args = new HashMap<>();
        revertCommand.execute(args);
        fail("Should have failed");
    }

    @Test(expected = ArgumentParsingException.class)
    public void requireRevisionArgument() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        revertCommand.execute(args);
        fail("Should have failed");
    }

    @Test
    public void callRevertOpertation() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.REVISION, "abcd");

        when(actionFactory.history(any())).thenReturn(historyAction);
        revertCommand.execute(args);

        verify(actionFactory).history(eq(Paths.get("test").toAbsolutePath()));
        verify(historyAction).rollback(eq(new SavedState("abcd")), any());
    }

    @Test
    public void useOfflineMavenSessionManagerIfOfflineSet() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.REVISION, "abcd");
        args.put(CliMain.OFFLINE, "true");

        when(actionFactory.history(any())).thenReturn(historyAction);
        revertCommand.execute(args);

        verify(actionFactory).history(eq(Paths.get("test").toAbsolutePath()));
        verify(historyAction).rollback(eq(new SavedState("abcd")), mavenSessionManager.capture());
        assertTrue(mavenSessionManager.getValue().isOffline());
    }

    @Test
    public void useLocalMavenRepoIfParameterSet() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.REVISION, "abcd");
        args.put(CliMain.LOCAL_REPO, "local-repo");

        when(actionFactory.history(any())).thenReturn(historyAction);
        revertCommand.execute(args);

        verify(actionFactory).history(eq(Paths.get("test").toAbsolutePath()));
        verify(historyAction).rollback(eq(new SavedState("abcd")), mavenSessionManager.capture());
        assertEquals(Paths.get("local-repo").toAbsolutePath(), mavenSessionManager.getValue().getProvisioningRepo());
    }
}