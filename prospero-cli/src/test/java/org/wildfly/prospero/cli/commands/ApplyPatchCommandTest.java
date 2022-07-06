package org.wildfly.prospero.cli.commands;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.ApplyPatchAction;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ApplyPatchCommandTest extends AbstractConsoleTest {

    @Mock
    private ApplyPatchAction applyPatchAction;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Override
    protected ActionFactory createActionFactory() {
        return new ActionFactory() {
            @Override
            public ApplyPatchAction applyPatch(Path targetPath, MavenSessionManager mavenSessionManager, Console console) {
                return applyPatchAction;
            }
        };
    }

    @Test
    public void requireDirFolder() throws Exception {
        int exitCode = commandLine.execute(CliConstants.APPLY_PATCH, CliConstants.PATCH_FILE, "foo");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s=<directory>'", CliConstants.DIR)));
    }

    @Test
    public void requirePatchFile() throws Exception {
        int exitCode = commandLine.execute(CliConstants.APPLY_PATCH, CliConstants.DIR, "foo");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s=<patchArchive>'", CliConstants.PATCH_FILE)));
    }

    @Test
    public void callApplyPatchAction() throws Exception {
        final Path testArchive = temp.newFile().toPath();
        int exitCode = commandLine.execute(CliConstants.APPLY_PATCH, CliConstants.DIR, "test",
                CliConstants.PATCH_FILE, testArchive.toString());
        Mockito.verify(applyPatchAction).apply(testArchive);
        assertEquals(ReturnCodes.SUCCESS, exitCode);
    }

    @Test
    public void dontAllowNonExistingPatchFile() throws Exception {
        int exitCode = commandLine.execute(CliConstants.APPLY_PATCH, CliConstants.DIR, "test",
                CliConstants.PATCH_FILE, "doesnt-exist.zip");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.fileDoesntExist(CliConstants.PATCH_FILE, Paths.get("doesnt-exist.zip"))));
    }
}