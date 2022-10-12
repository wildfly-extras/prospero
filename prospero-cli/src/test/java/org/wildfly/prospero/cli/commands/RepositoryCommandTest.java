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

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.test.MetadataTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Ignore
public class RepositoryCommandTest extends AbstractConsoleTest {

    private static final String REPO_ID = "repo1";
    private static final String REPO_URL = "file:/tmp/repo1";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path dir;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        this.dir = tempDir.newFolder().toPath();
        RemoteRepository repo = new RemoteRepository.Builder(REPO_ID, "default", REPO_URL).build();
        MetadataTestUtils.createInstallationMetadata(dir, null, null);
        MetadataTestUtils.createGalleonProvisionedState(dir);
    }

    @Test
    public void testListInvalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.LIST);

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(RepositoryCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void testAddInvalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.ADD, "repo2", "file:/tmp/repo2");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(RepositoryCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void testRemoveInvalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.REMOVE, REPO_ID);

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(RepositoryCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void testList() {
        int exitCode = commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.LIST, CliConstants.DIR, dir.toString());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        assertTrue(getStandardOutput().contains(REPO_ID));
        assertTrue(getStandardOutput().contains(REPO_URL));
    }

    @Test
    public void testAddInvalidArguments() {
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.ADD, "repo2", "file:/tmp/repo2"));
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.ADD, CliConstants.DIR, dir.toString()));
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.ADD, CliConstants.DIR, dir.toString(), "repo2"));
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.ADD, CliConstants.DIR, dir.toString(), "repo2", "invalid url"));
    }

    @Test
    public void testAdd() throws MetadataException {
        int exitCode = commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.ADD, CliConstants.DIR, dir.toString(),
                "repo2", "file:/tmp/repo2");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        InstallationMetadata installationMetadata = new InstallationMetadata(dir);
        assertThat(installationMetadata.getProsperoConfig().getRepositories()).containsExactly(
                new RepositoryRef(REPO_ID, REPO_URL),
                new RepositoryRef("repo2", "file:/tmp/repo2")
        );
    }

    @Test
    public void testRemoveInvalidArguments() {
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.REMOVE));
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.REMOVE, CliConstants.DIR, dir.toString()));
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.REMOVE, CliConstants.DIR, dir.toString(), "repo2"));
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.REMOVE, CliConstants.DIR, dir.toString(), "repo1", "invalid param"));
    }

    @Test
    public void testRemove() throws MetadataException {
        int exitCode = commandLine.execute(CliConstants.Commands.REPO, CliConstants.Commands.REMOVE, CliConstants.DIR, dir.toString(),
                REPO_ID);

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        InstallationMetadata installationMetadata = new InstallationMetadata(dir);
        assertThat(installationMetadata.getProsperoConfig().getRepositories()).isEmpty();
    }

}
