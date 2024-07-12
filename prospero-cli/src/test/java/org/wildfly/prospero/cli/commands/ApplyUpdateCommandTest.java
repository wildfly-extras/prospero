/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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
import org.wildfly.prospero.api.FileConflict;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.updates.MarkerFile;
import org.wildfly.prospero.updates.UpdateSet;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ApplyUpdateCommandTest extends AbstractConsoleTest {

    @Mock
    private ActionFactory actionFactory;

    @Mock
    private ApplyCandidateAction applyCandidateAction;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Override
    protected ActionFactory createActionFactory() {
        return actionFactory;
    }

    private Path installationDir;
    private Path updateDir;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(applyCandidateAction.verifyCandidate(ApplyCandidateAction.Type.UPDATE)).thenReturn(ApplyCandidateAction.ValidationResult.OK);
        when(actionFactory.applyUpdate(any(), any())).thenReturn(applyCandidateAction);
        when(applyCandidateAction.findUpdates()).thenReturn(new UpdateSet(Collections.emptyList()));
    }

    @Test
    public void requireUpdateDirPresent() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY);
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s",
                CliConstants.CANDIDATE_DIR)));
    }

    @Test
    public void callUpdateAction() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");
        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(getErrorOutput(), ReturnCodes.SUCCESS, exitCode);
        verify(applyCandidateAction).applyUpdate(ApplyCandidateAction.Type.UPDATE);
    }

    @Test
    public void targetFolderNotValidInstallation() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = temp.newFolder("target").toPath();

        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(targetPath)
                .getMessage()));
    }

    @Test
    public void updateFolderNotValidInstallation() throws Exception {
        final Path updatePath = temp.newFolder("update").toPath();
        final Path targetPath = mockInstallation("target");

        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(updatePath)
                .getMessage()));
    }

    @Test
    public void targetFolderNeedsToBeProsperoInstallation() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = temp.newFolder().toPath();
        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(getErrorOutput(), ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(targetPath)
                .getMessage()));
        verify(applyCandidateAction, never()).applyUpdate(ApplyCandidateAction.Type.UPDATE);
    }

    @Test
    public void updateCandidateNeedsToContainUpdateMarkupFile() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");
        Files.deleteIfExists(updatePath.resolve(MarkerFile.UPDATE_MARKER_FILE));
        when(applyCandidateAction.verifyCandidate(ApplyCandidateAction.Type.UPDATE)).thenReturn(ApplyCandidateAction.ValidationResult.NOT_CANDIDATE);

        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(getErrorOutput(), ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.notCandidate(updatePath)
                .getMessage()));
        verify(applyCandidateAction, never()).applyUpdate(ApplyCandidateAction.Type.UPDATE);
    }

    @Test
    public void updateCandidateNeedsToContainValidUpdateMarkupFile() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");

        when(applyCandidateAction.verifyCandidate(ApplyCandidateAction.Type.UPDATE)).thenReturn(ApplyCandidateAction.ValidationResult.NOT_CANDIDATE);

        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(getErrorOutput(), ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.notCandidate(updatePath)
                .getMessage()));
        verify(applyCandidateAction, never()).applyUpdate(ApplyCandidateAction.Type.UPDATE);
    }

    @Test
    public void testAskForConfirmationIfConflictsPresent() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");
        when(applyCandidateAction.getConflicts()).thenReturn(List.of(mock(FileConflict.class)));
        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(getErrorOutput(), ReturnCodes.SUCCESS, exitCode);
        assertEquals(1, askedConfirmation);
    }

    @Test
    public void noConflictArgumentFailsCommand_WhenConflictsAreFound() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");
        when(applyCandidateAction.getConflicts()).thenReturn(List.of(mock(FileConflict.class)));

        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString(),
                CliConstants.NO_CONFLICTS_ONLY);

        assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
        assertThat(getErrorOutput())
                .contains(CliMessages.MESSAGES.cancelledByConfilcts().getMessage());

        verify(applyCandidateAction, Mockito.never()).applyUpdate(any());
    }

    @Test
    public void noConflictArgumentHasNoEffect_WhenNoConflictsAreFound() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");
        when(applyCandidateAction.getConflicts()).thenReturn(Collections.emptyList());

        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString(),
                CliConstants.NO_CONFLICTS_ONLY);

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(applyCandidateAction).applyUpdate(ApplyCandidateAction.Type.UPDATE);
    }

    @Test
    public void dryRun_DoesntCallApplyAction() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");
        when(applyCandidateAction.getConflicts()).thenReturn(Collections.emptyList());

        int exitCode = commandLine.execute(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                CliConstants.CANDIDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString(),
                CliConstants.DRY_RUN);

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(applyCandidateAction, Mockito.never()).applyUpdate(any());
    }

    private Path mockInstallation(String target) throws IOException, MetadataException, XMLStreamException {
        final Path targetPath = temp.newFolder(target).toPath();
        MetadataTestUtils.createInstallationMetadata(targetPath).close();
        MetadataTestUtils.createGalleonProvisionedState(targetPath, UpdateCommand.PROSPERO_FP_GA);

        new MarkerFile("abcd1234", ApplyCandidateAction.Type.UPDATE).write(targetPath);
        return targetPath;
    }

}