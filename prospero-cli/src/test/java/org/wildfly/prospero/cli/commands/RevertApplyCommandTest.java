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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RevertApplyCommandTest extends AbstractConsoleTest {

    @Mock
    private InstallationHistoryAction historyAction;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path installationDir;

    @Override
    protected ActionFactory createActionFactory() {
        return new ActionFactory() {
            @Override
            public InstallationHistoryAction history(Path targetPath, Console console) {
                return historyAction;
            }
        };
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        this.installationDir = tempDir.newFolder().toPath();
        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir);
    }
    @Test
    public void invalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.APPLY,
                CliConstants.UPDATE_DIR, "update_test");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(RevertCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void requireUpdateDirArgument() {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.APPLY,
                CliConstants.DIR, installationDir.toString());

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format(
                "Missing required option: '%s=<updateDirectory>'", CliConstants.UPDATE_DIR)));
    }

    @Test
    public void callApplyOperation() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.APPLY,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.UPDATE_DIR, "update_test");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(historyAction).applyRevert(Path.of("update_test").toAbsolutePath());
    }
}