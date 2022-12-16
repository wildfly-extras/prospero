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
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.ApplyUpdateAction;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.test.MetadataTestUtils;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ApplyUpdateCommandTest extends AbstractConsoleTest {

    @Mock
    private ActionFactory actionFactory;

    @Mock
    private ApplyUpdateAction applyUpdateAction;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Override
    protected ActionFactory createActionFactory() {
        return actionFactory;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(applyUpdateAction.verifyUpdateCandidate()).thenReturn(true);
        when(actionFactory.applyUpdate(any(), any())).thenReturn(applyUpdateAction);
    }

    @Test
    public void requireUpdateDirPresent() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.APPLY_UPDATE);
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s",
                CliConstants.UPDATE_DIR)));
    }

    @Test
    public void callUpdateAction() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");
        int exitCode = commandLine.execute(CliConstants.Commands.APPLY_UPDATE,
                CliConstants.UPDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(getErrorOutput(), ReturnCodes.SUCCESS, exitCode);
        verify(applyUpdateAction).applyUpdate();
    }

    @Test
    public void targetFolderNotValidInstallation() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = temp.newFolder("target").toPath();

        int exitCode = commandLine.execute(CliConstants.Commands.APPLY_UPDATE,
                CliConstants.UPDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(targetPath)
                .getMessage()));
    }

    @Test
    public void updateFolderNotValidInstallation() throws Exception {
        final Path updatePath = temp.newFolder("update").toPath();
        final Path targetPath = mockInstallation("target");

        int exitCode = commandLine.execute(CliConstants.Commands.APPLY_UPDATE,
                CliConstants.UPDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(updatePath)
                .getMessage()));
    }

    @Test
    public void targetFolderNeedsToBeProsperoInstallation() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");
        int exitCode = commandLine.execute(CliConstants.Commands.APPLY_UPDATE,
                CliConstants.UPDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(getErrorOutput(), ReturnCodes.SUCCESS, exitCode);
        verify(applyUpdateAction).applyUpdate();
    }

    @Test
    public void updateCandidateNeedsToContainUpdateMarkupFile() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");
        Files.deleteIfExists(updatePath.resolve(ApplyUpdateAction.UPDATE_MARKER_FILE));

        int exitCode = commandLine.execute(CliConstants.Commands.APPLY_UPDATE,
                CliConstants.UPDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(getErrorOutput(), ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidUpdateCandidate(updatePath)
                .getMessage()));
        verify(applyUpdateAction, never()).applyUpdate();
    }

    @Test
    public void updateCandidateNeedsToContainValidUpdateMarkupFile() throws Exception {
        final Path updatePath = mockInstallation("update");
        final Path targetPath = mockInstallation("target");

        when(applyUpdateAction.verifyUpdateCandidate()).thenReturn(false);

        int exitCode = commandLine.execute(CliConstants.Commands.APPLY_UPDATE,
                CliConstants.UPDATE_DIR, updatePath.toString(),
                CliConstants.DIR, targetPath.toString());

        Assert.assertEquals(getErrorOutput(), ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.updateCandidateStateNotMatched(targetPath, updatePath)
                .getMessage()));
        verify(applyUpdateAction, never()).applyUpdate();
    }

    private Path mockInstallation(String target) throws IOException, MetadataException, XMLStreamException {
        final Path targetPath = temp.newFolder(target).toPath();
        MetadataTestUtils.createInstallationMetadata(targetPath);
        MetadataTestUtils.createGalleonProvisionedState(targetPath, UpdateCommand.PROSPERO_FP_GA);

        Files.writeString(targetPath.resolve(ApplyUpdateAction.UPDATE_MARKER_FILE), "abcd1234");
        return targetPath;
    }

}