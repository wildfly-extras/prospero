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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.jboss.galleon.ProvisioningException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.actions.ApplyCandidateAction;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.updates.UpdateSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RevertPerformCommandTest extends AbstractMavenCommandTest {

    @Mock
    private InstallationHistoryAction historyAction;

    @Mock
    private ApplyCandidateAction applyCandidateAction;

    @Captor
    private ArgumentCaptor<MavenOptions> mavenOptions;

    @Captor
    private ArgumentCaptor<List<Repository>> repositories;

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

            @Override
            public ApplyCandidateAction applyUpdate(Path installationPath, Path updatePath) throws OperationException, ProvisioningException {
                return applyCandidateAction;
            }
        };
    }
    @Before
    public void setUp() throws Exception {
        super.setUp();

        this.installationDir = tempDir.newFolder().toPath();
        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir);

        when(applyCandidateAction.findUpdates()).thenReturn(new UpdateSet(Collections.emptyList()));
    }
    @Test
    public void invalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.PERFORM, CliConstants.REVISION, "abcd");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(RevertCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void requireRevisionArgument() {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.PERFORM, CliConstants.DIR, installationDir.toString());

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format(
                "Missing required option: '%s=<revision>'", CliConstants.REVISION)));
    }

    @Test
    public void callRevertOpertation() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.PERFORM, CliConstants.DIR, installationDir.toString(),
                CliConstants.REVISION, "abcd");

        assertEquals(ReturnCodes.SUCCESS_LOCAL_CHANGES, exitCode);
        verify(historyAction).prepareRevert(eq(new SavedState("abcd")), any(), any(), any());
        verify(applyCandidateAction).applyUpdate(ApplyCandidateAction.Type.ROLLBACK);
    }

    @Test
    public void useOfflineMavenSessionManagerIfOfflineSet() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.PERFORM, CliConstants.DIR, installationDir.toString(),
                CliConstants.REVISION, "abcd",
                CliConstants.OFFLINE, CliConstants.LOCAL_CACHE, "local-repo");

        assertEquals(ReturnCodes.SUCCESS_LOCAL_CHANGES, exitCode);
        verify(historyAction).prepareRevert(eq(new SavedState("abcd")), mavenOptions.capture(), any(), any());
        verify(applyCandidateAction).applyUpdate(ApplyCandidateAction.Type.ROLLBACK);
        verify(applyCandidateAction).applyUpdate(ApplyCandidateAction.Type.ROLLBACK);
        assertTrue(mavenOptions.getValue().isOffline());
    }

    @Test
    public void passRemoteRepositories() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.REVERT, CliConstants.Commands.PERFORM, CliConstants.DIR, installationDir.toString(),
                CliConstants.REVISION, "abcd",
                CliConstants.REPOSITORIES, "http://temp.repo.te",
                CliConstants.LOCAL_CACHE, "local-repo");

        assertEquals(ReturnCodes.SUCCESS_LOCAL_CHANGES, exitCode);
        verify(historyAction).prepareRevert(eq(new SavedState("abcd")), any(), repositories.capture(), any());

        assertThat(repositories.getValue())
                .map(Repository::getUrl)
                .containsExactly("http://temp.repo.te");
    }

    @Override
    protected MavenOptions getCapturedMavenOptions() throws Exception {
        verify(historyAction).prepareRevert(eq(new SavedState("abcd")), mavenOptions.capture(), any(), any());
        return mavenOptions.getValue();
    }

    @Override
    protected String[] getDefaultArguments() {
        return new String[]{CliConstants.Commands.REVERT, CliConstants.Commands.PERFORM, CliConstants.DIR, installationDir.toString(),
                CliConstants.REVISION, "abcd"};
    }
}