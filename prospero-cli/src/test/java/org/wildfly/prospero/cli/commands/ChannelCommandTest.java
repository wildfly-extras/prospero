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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.test.MetadataTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class ChannelCommandTest extends AbstractConsoleTest {

    private static final String GA = "g:a";
    private static final String GAV = "g:a:v";
    private static final String URL = "file:/a:b";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path dir;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        this.dir = tempDir.newFolder().toPath();
        Channel gaChannel = new Channel("test1", "", null, null,
                List.of(new Repository("test", "http://test.org")),
                ChannelManifestCoordinate.create(null, GA));
        Channel gavChannel = new Channel("test2", "", null, null,
                List.of(new Repository("test", "http://test.org")),
                ChannelManifestCoordinate.create(null, GAV));
        Channel urlChannel = new Channel("test3", "", null, null,
                List.of(new Repository("test", "http://test.org")),
                ChannelManifestCoordinate.create(URL, null));
        MetadataTestUtils.createInstallationMetadata(dir, new ChannelManifest(null, null, null),
                Arrays.asList(gaChannel, gavChannel, urlChannel));
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
    public void testAddEmptyRepository() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "channel-0",
                CliConstants.CHANNEL_MANIFEST, "org.test:test",
                CliConstants.REPOSITORIES, "");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidRepositoryDefinition("")
                .getMessage()));
    }

    @Test
    public void testAddInvalidRepositoryTooManyParts() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "channel-0",
                CliConstants.CHANNEL_MANIFEST, "org.test:test",
                CliConstants.REPOSITORIES, "id::http://test.te::foo");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidRepositoryDefinition("id::http://test.te::foo")
                .getMessage()));
    }

    @Test
    public void testAdd() throws MetadataException, MalformedURLException {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "channel-0",
                CliConstants.CHANNEL_MANIFEST, "org.test:test",
                CliConstants.REPOSITORIES, "test_repo::http://test.te");
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "channel-1",
                CliConstants.CHANNEL_MANIFEST, "g:a2",
                CliConstants.REPOSITORIES, "test_repo::http://test.te");
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "channel-2",
                CliConstants.CHANNEL_MANIFEST, "file:/path",
                CliConstants.REPOSITORIES, "test_repo::http://test.te");
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);

        InstallationMetadata installationMetadata = new InstallationMetadata(dir);
        assertThat(installationMetadata.getProsperoConfig().getChannels())
                .flatMap(c->c.getRepositories())
                .map(r->Tuple.tuple(r.getId(), r.getUrl()))
                .containsExactly(
                        Tuple.tuple("test", "http://test.org"),
                        Tuple.tuple("test", "http://test.org"),
                        Tuple.tuple("test", "http://test.org"),
                        Tuple.tuple("test_repo", "http://test.te"),
                        Tuple.tuple("test_repo", "http://test.te"),
                        Tuple.tuple("test_repo", "http://test.te")
                );
        assertThat(installationMetadata.getProsperoConfig().getChannels())
                .map(c->c.getManifestRef())
                .map(r->Tuple.tuple(r.getGav(), r.getUrl()))
                .containsExactly(
                        Tuple.tuple(GA, null),
                        Tuple.tuple(GAV, null),
                        Tuple.tuple(null, new URL(URL)),
                        Tuple.tuple("org.test:test", null),
                        Tuple.tuple("g:a2", null),
                        Tuple.tuple(null, new URL("file:/path"))
                );
    }

    @Test
    public void testAddInvalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.CHANNEL_NAME, "channel-0",
                CliConstants.CHANNEL_MANIFEST, "org.test:test",
                CliConstants.REPOSITORIES, "test_repo::http://test.te");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(ChannelCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void testRemoveInvalidInstallationDir() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.CHANNEL_NAME, "test2");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(ChannelCommand.currentDir())
                .getMessage()));
    }

    @Test
    public void testList() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.LIST,
                CliConstants.DIR, dir.toString());
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        Assert.assertEquals(3, getStandardOutput().lines().filter(l->l.contains("manifest")).count());
    }

    @Test
    public void testAddDuplicate() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(), GAV);
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(), GA);
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(), URL);
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
    }

    @Test
    public void testRemove() throws Exception {
        try (InstallationMetadata installationMetadata = new InstallationMetadata(dir)) {
            installationMetadata.getProsperoConfig().getChannels().forEach(System.out::println);
        }
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "test2");
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        try (InstallationMetadata installationMetadata = new InstallationMetadata(dir)) {
            assertThat(installationMetadata.getProsperoConfig().getChannels())
                    .map(c->c.getManifestRef())
                    .map(r->Tuple.tuple(r.getGav(), r.getUrl()))
                    .containsExactly(
                            Tuple.tuple(GA, null),
                            Tuple.tuple(null, new URL(URL))
                    );
        }

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "test1");
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        try (InstallationMetadata installationMetadata = new InstallationMetadata(dir)) {
            assertThat(installationMetadata.getProsperoConfig().getChannels())
                    .map(c->c.getManifestRef())
                    .map(r->Tuple.tuple(r.getGav(), r.getUrl()))
                    .containsExactly(
                            Tuple.tuple(null, new URL(URL))
                    );
        }

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "test3");
        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        try (InstallationMetadata installationMetadata = new InstallationMetadata(dir)) {
            assertThat(installationMetadata.getProsperoConfig().getChannels())
                    .map(c->c.getManifestRef())
                    .map(r->Tuple.tuple(r.getGav(), r.getUrl()))
                    .isEmpty();
        }
    }

    @Test
    public void testRemoveNonExisting() {
        int exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME,  "test4");
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "test-1");
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);

        // remove the channel twice
        commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "test1");
        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.REMOVE,
                CliConstants.DIR, dir.toString(),
                CliConstants.CHANNEL_NAME, "test1");
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
    }

}
