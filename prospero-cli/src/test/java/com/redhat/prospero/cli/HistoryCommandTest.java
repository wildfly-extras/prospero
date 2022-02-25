package com.redhat.prospero.cli;

import com.redhat.prospero.actions.Console;
import com.redhat.prospero.actions.InstallationHistory;
import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.api.SavedState;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HistoryCommandTest {

    @Mock
    private InstallationHistory historyAction;

    @Mock
    private CliMain.ActionFactory actionFactory;

    @Mock
    private Console console;

    private HistoryCommand historyCommand;

    @Before
    public void setUp() {
        historyCommand = new HistoryCommand(actionFactory, console);
    }

    @Test(expected = ArgumentParsingException.class)
    public void requireDirFolder() throws Exception {
        Map<String, String> args = new HashMap<>();
        historyCommand.execute(args);
        fail("Should have failed");
    }

    @Test
    public void displayListOfStates() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");

        when(actionFactory.history(any())).thenReturn(historyAction);
        when(historyAction.getRevisions()).thenReturn(Arrays.asList(new SavedState("abcd", Instant.ofEpochSecond(System.currentTimeMillis()), SavedState.Type.INSTALL)));
        historyCommand.execute(args);

        verify(actionFactory).history(eq(Paths.get("test").toAbsolutePath()));
        verify(historyAction).getRevisions();
        verify(console).println(contains("abcd"));
    }

    @Test
    public void displayDetailsOfStateIfRevisionSet() throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.REVISION, "abcd");

        when(actionFactory.history(any())).thenReturn(historyAction);
        when(historyAction.compare(any())).thenReturn(Arrays.asList(new ArtifactChange(new DefaultArtifact("foo", "bar", "jar", "1.1"), new DefaultArtifact("foo", "bar", "jar", "1.2"))));
        historyCommand.execute(args);

        verify(actionFactory).history(eq(Paths.get("test").toAbsolutePath()));
        verify(historyAction).compare(eq(new SavedState("abcd")));
        verify(console).println(contains("foo:bar"));
    }
}