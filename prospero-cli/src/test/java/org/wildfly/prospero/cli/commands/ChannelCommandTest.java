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
import org.wildfly.channel.MavenCoordinate;
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

    private static final MavenCoordinate GA = new MavenCoordinate("g", "a", null);
    private static final MavenCoordinate GAV = new MavenCoordinate("g", "a", "v");
    private static final String URL = "file:/a:b";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path dir;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        this.dir = tempDir.newFolder().toPath();
        Channel gaChannel = createChannel("test1", ChannelManifestCoordinate.create(null, GA));
        Channel gavChannel = createChannel("test2", ChannelManifestCoordinate.create(null, GAV));
        Channel urlChannel = createChannel("test3", ChannelManifestCoordinate.create(URL, null));
        MetadataTestUtils.createInstallationMetadata(dir, new ChannelManifest(null, null, null, null),
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
                .map(c->c.getManifestCoordinate())
                .map(r->Tuple.tuple(r.getMaven(), r.getUrl()))
                .containsExactly(
                        Tuple.tuple(GA, null),
                        Tuple.tuple(GAV, null),
                        Tuple.tuple(null, new URL(URL)),
                        Tuple.tuple(new MavenCoordinate("org.test", "test", null), null),
                        Tuple.tuple(new MavenCoordinate("g", "a2", null), null),
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
                CliConstants.DIR, dir.toString(), toGav(GAV));
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);

        exitCode = commandLine.execute(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                CliConstants.DIR, dir.toString(), toGav(GA));
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
                    .map(c->c.getManifestCoordinate())
                    .map(r->Tuple.tuple(r.getMaven(), r.getUrl()))
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
                    .map(c->c.getManifestCoordinate())
                    .map(r->Tuple.tuple(r.getMaven(), r.getUrl()))
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
                    .map(c->c.getManifestCoordinate())
                    .map(r->Tuple.tuple(r.getMaven(), r.getUrl()))
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

    private static Channel createChannel(String name, ChannelManifestCoordinate coord) throws MalformedURLException {
        return new Channel(name, "", null,
                List.of(new Repository("test", "http://test.org")),
                coord, null, null);
    }

    private static String toGav(MavenCoordinate coord) {
        final String ga = coord.getGroupId() + ":" + coord.getArtifactId();
        if (coord.getVersion() != null && !coord.getVersion().isEmpty()) {
            return ga + ":" + coord.getVersion();
        }
        return ga;
    }

}
