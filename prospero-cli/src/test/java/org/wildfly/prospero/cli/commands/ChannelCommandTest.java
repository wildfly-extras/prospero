/*
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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.assertj.core.groups.Tuple;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.test.MetadataTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class ChannelCommandTest extends AbstractConsoleTest {

    private static final String GAV = "g:a:v";
    private static final String URL = "file:/a:b";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path dir;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        this.dir = tempDir.newFolder().toPath();
        ChannelRef gavChannel = new ChannelRef(GAV, null);
        ChannelRef urlChannel = new ChannelRef(null, URL);
        MetadataTestUtils.createInstallationMetadata(dir, Arrays.asList(gavChannel, urlChannel), Collections.emptyList());
        MetadataTestUtils.createGalleonProvisionedState(dir);
    }

    @Test
    public void testListInvalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.LIST);

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(ChannelCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void testAddInvalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD, "g:a:v2");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(ChannelCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void testRemoveInvalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE, GAV);

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(ChannelCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void testList() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.LIST,
                CliConstants.DIR, dir.toString());
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        Assert.assertEquals(2, getStandardOutput().lines().count());
    }

    @Test
    public void testAdd() throws MetadataException {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(), "g:a:v2");
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(), "file:/path");
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);

        InstallationMetadata installationMetadata = new InstallationMetadata(dir);
        assertThat(installationMetadata.getProsperoConfig().getChannels())
                .extracting("gav", "url")
                .containsExactly(
                        Tuple.tuple(null, "file:/path"),
                        Tuple.tuple("g:a:v2", null),
                        Tuple.tuple(GAV, null),
                        Tuple.tuple(null, URL)
                );
    }

    @Test
    public void testAddDuplicate() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(), GAV);
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(), URL);
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
    }

    @Test
    public void testRemove() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(), GAV);
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(), URL);
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
    }

    @Test
    public void testRemoveNonExisting() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(), "g:a:v2");
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(), "file:/path");
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
    }

}
