package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistory;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HistoryCommandTest extends AbstractConsoleTest {

    @Mock
    private InstallationHistory historyAction;

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
    public void requireDirFolder() {
        int exitCode = commandLine.execute(CliConstants.HISTORY);
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s=<directory>'", CliConstants.DIR)));
    }

    @Test
    public void displayListOfStates() throws Exception {
        when(historyAction.getRevisions()).thenReturn(Arrays.asList(
                new SavedState("abcd", Instant.ofEpochSecond(System.currentTimeMillis()), SavedState.Type.INSTALL)));

        int exitCode = commandLine.execute(CliConstants.HISTORY, CliConstants.DIR, "test");
        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(historyAction).getRevisions();
        assertTrue(getStandardOutput().contains("abcd"));
    }

    @Test
    public void displayDetailsOfStateIfRevisionSet() throws Exception {
        when(historyAction.compare(any())).thenReturn(Arrays.asList(new ArtifactChange(
                        new DefaultArtifact("foo", "bar", "jar", "1.1"),
                        new DefaultArtifact("foo", "bar", "jar", "1.2"))));

        int exitCode = commandLine.execute(CliConstants.HISTORY, CliConstants.DIR, "test", CliConstants.REVISION, "abcd");
        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(historyAction).compare(eq(new SavedState("abcd")));
        assertTrue(getStandardOutput().contains("foo:bar"));
    }
}