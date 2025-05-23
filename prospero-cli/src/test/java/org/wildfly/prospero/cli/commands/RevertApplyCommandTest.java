/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.cli.commands;

import org.jboss.galleon.ProvisioningException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.ApplyCandidateAction;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.FileConflict;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.updates.UpdateSet;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RevertApplyCommandTest extends AbstractConsoleTest {

    @Mock
    private InstallationHistoryAction historyAction;

    @Mock
    private ApplyCandidateAction applyCandidateAction;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path installationDir;
    private Path updateDir;

    @Override
    protected ActionFactory createActionFactory() {
        return new ActionFactory() {
            @Override
            public InstallationHistoryAction history(Path targetPath, Console console) {
                return historyAction;
            }

            @Override
            public ApplyCandidateAction applyUpdate(Path installationPath, Path updatePath) throws OperationException, ProvisioningException {
                return applyCandidateAction;
            }
        };
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        this.installationDir = tempDir.newFolder("base").toPath();
        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir);

        this.updateDir = tempDir.newFolder("update").toPath();
        MetadataTestUtils.createInstallationMetadata(updateDir);
        MetadataTestUtils.createGalleonProvisionedState(updateDir);

        when(applyCandidateAction.findUpdates()).thenReturn(new UpdateSet(Collections.emptyList()));
        when(applyCandidateAction.getCandidateRevision()).thenReturn(new SavedState("abcd1234"));
    }
    @Test
    public void invalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, "update_test");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(RevertCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void requireCandidateDirArgument() {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.APPLY,
                CliConstants.DIR, installationDir.toString());

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format(
                "Missing required option: '%s=<candidateDirectory>'", CliConstants.CANDIDATE_DIR)));
    }

    @Test
    public void callApplyOperation() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.APPLY,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CANDIDATE_DIR, updateDir.toString());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(applyCandidateAction).applyUpdate(ApplyCandidateAction.Type.REVERT);
    }

    @Test
    public void noConflictArgumentFailsCommand_WhenConflictsAreFound() throws Exception {
        when(applyCandidateAction.getConflicts()).thenReturn(List.of(mock(FileConflict.class)));

        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.APPLY,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CANDIDATE_DIR, updateDir.toString(),
                CliConstants.NO_CONFLICTS_ONLY);

        assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
        assertThat(getErrorOutput())
                .contains(CliMessages.MESSAGES.cancelledByConfilcts().getMessage());

        verify(applyCandidateAction, Mockito.never()).applyUpdate(any());
    }

    @Test
    public void noConflictArgumentHasNoEffect_WhenNoConflictsAreFound() throws Exception {
        when(applyCandidateAction.getConflicts()).thenReturn(Collections.emptyList());

        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.APPLY,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CANDIDATE_DIR, updateDir.toString(),
                CliConstants.NO_CONFLICTS_ONLY);

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(applyCandidateAction).applyUpdate(ApplyCandidateAction.Type.REVERT);
    }

    @Test
    public void dryRun_DoesntCallApplyAction() throws Exception {
        when(applyCandidateAction.getConflicts()).thenReturn(Collections.emptyList());

        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updateDir.toString(),
                CliConstants.DIR, installationDir.toString(),
                CliConstants.DRY_RUN);

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(applyCandidateAction, Mockito.never()).applyUpdate(any());
    }
}
