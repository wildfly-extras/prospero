/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.cli.commands;

import org.junit.Before;
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
import org.wildfly.prospero.test.MetadataTestUtils;
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

    private Path installationDir;

    @Override
    protected ActionFactory createActionFactory() {
        return new ActionFactory() {
            @Override
            public ApplyPatchAction applyPatch(Path targetPath, MavenSessionManager mavenSessionManager, Console console) {
                return applyPatchAction;
            }
        };
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        installationDir = temp.newFolder().toPath();
        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir);
    }

    @Test
    public void invalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.APPLY_PATCH, CliConstants.PATCH_FILE, "foo");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(ApplyPatchCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void requirePatchFile() {
        int exitCode = commandLine.execute(CliConstants.APPLY_PATCH, CliConstants.DIR, "foo");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s=%s'",
                CliConstants.PATCH_FILE, CliConstants.PATH)));
    }

    @Test
    public void callApplyPatchAction() throws Exception {
        final Path testArchive = temp.newFile().toPath();
        int exitCode = commandLine.execute(CliConstants.APPLY_PATCH, CliConstants.DIR, installationDir.toString(),
                CliConstants.PATCH_FILE, testArchive.toString());
        Mockito.verify(applyPatchAction).apply(testArchive);
        assertEquals(ReturnCodes.SUCCESS, exitCode);
    }

    @Test
    public void dontAllowNonExistingPatchFile() {
        int exitCode = commandLine.execute(CliConstants.APPLY_PATCH, CliConstants.DIR, installationDir.toString(),
                CliConstants.PATCH_FILE, "doesnt-exist.zip");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.fileDoesntExist(CliConstants.PATCH_FILE, Paths.get("doesnt-exist.zip"))));
    }
}