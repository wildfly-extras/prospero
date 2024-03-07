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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.galleon.universe.FeaturePackLocation;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.InvalidChannelMetadataException;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.KnownFeaturePacks;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.test.MetadataTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InstallCommandTest extends AbstractMavenCommandTest {

    public static final String KNOWN_FPL = "known-fpl";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private ActionFactory actionFactory;

    @Mock
    private ProvisioningAction provisionAction;

    @Captor
    private ArgumentCaptor<GalleonProvisioningConfig> configCaptor;

    @Captor
    private ArgumentCaptor<List<Channel>> channelCaptor;

    @Captor
    private ArgumentCaptor<MavenOptions> mavenOptions;

    @Override
    protected ActionFactory createActionFactory() {
        return actionFactory;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(actionFactory.install(any(), any(), any())).thenReturn(provisionAction);
    }

    @Test
    public void errorIfTargetPathIsNotPresent() {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL);
        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format("Missing required option: '%s=<directory>'",
                CliConstants.DIR)));
    }

    @Test
    public void errorIfFplIsNotPresent() {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(String.format(
                "Missing required argument (specify one of these): (%s=%s | %s=%s | %s=%s)",
                CliConstants.PROFILE, CliConstants.PROFILE_REFERENCE, CliConstants.FPL, CliConstants.FEATURE_PACK_REFERENCE, CliConstants.DEFINITION, CliConstants.PATH)));
    }

    @Test
    public void errorIfChannelsIsNotPresentAndUsingCustomFplOnInstall() {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.FPL, "foo:bar");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue("output: " + getErrorOutput(), getErrorOutput().contains(CliMessages.MESSAGES
                .channelsMandatoryWhenCustomFpl(String.join(",", KnownFeaturePacks.getNames())).getMessage()));
    }

    @Test
    public void errorIfChannelsIsNotValid() throws Exception {
        final File channelsFile = temporaryFolder.newFile();
        Files.writeString(channelsFile.toPath(), "schemaVersion: 2.0.0\n");
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test", CliConstants.PROFILE, KNOWN_FPL,
                CliConstants.CHANNELS, channelsFile.getAbsolutePath());
        assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
        assertTrue("output: " + getErrorOutput(), getErrorOutput().contains(ProsperoLogger.ROOT_LOGGER
                .invalidChannel(new InvalidChannelMetadataException(null, Collections.emptyList())).getMessage()));
    }

    @Test
    public void callProvisionOnInstallCommandWithCustomFpl() throws Exception {
        final File channelsFile = temporaryFolder.newFile();
        Channel channel = createChannel("test", "test", "http://test.org", "org.test");
        MetadataTestUtils.writeChannels(channelsFile.toPath(), List.of(channel));

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.FPL, "org.wildfly:wildfly-ee-galleon-pack",
                CliConstants.CHANNELS, channelsFile.getAbsolutePath());
        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(configCaptor.capture(), channelCaptor.capture(), any());
        assertThat(configCaptor.getValue().getFeaturePackDeps())
                .map(fp->fp.getLocation().getProducerName())
                .containsExactly("org.wildfly:wildfly-ee-galleon-pack::zip");
    }

    @Test
    public void callProvisionOnInstallKnownCommand() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test", CliConstants.PROFILE, KNOWN_FPL);
        commandLine.getOut();
        commandLine.getErr();

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(configCaptor.capture(), channelCaptor.capture(), any());
        assertThat(configCaptor.getValue().getFeaturePackDeps())
                .map(fp->fp.getLocation().getProducerName())
                .containsExactly("org.wildfly.core:wildfly-core-galleon-pack::zip");
    }

    @Test
    public void callProvisionOnInstallKnownFplOverrideChannelsCommand() throws Exception {
        final File channelsFile = temporaryFolder.newFile();
        Channel channel = createChannel("dev", "wildfly-channel", "http://test.test", "org.wildfly");
        MetadataTestUtils.writeChannels(channelsFile.toPath(), List.of(channel));

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test", CliConstants.PROFILE, KNOWN_FPL,
                CliConstants.CHANNELS, channelsFile.getAbsolutePath());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(configCaptor.capture(), channelCaptor.capture(), any());
        assertThat(configCaptor.getValue().getFeaturePackDeps())
                .map(fp->fp.getLocation().getProducerName())
                .containsExactly("org.wildfly.core:wildfly-core-galleon-pack::zip");
        assertThat(channelCaptor.getValue())
                .flatMap(Channel::getRepositories)
                .map(Repository::getId)
                .containsExactly("dev");
    }

    @Test
    public void usingProvisionDefinitonRequiresChannel() throws Exception {
        final File provisionDefinitionFile = temporaryFolder.newFile("provision.xml");
        try(Provisioning p = new GalleonBuilder().newProvisioningBuilder().build()) {
            p.storeProvisioningConfig(GalleonProvisioningConfig.builder()
                        .addFeaturePackDep(FeaturePackLocation.fromString("org.wildfly.core:wildfly-core-galleon-pack::zip"))
                        .build(), provisionDefinitionFile.toPath());
        }

        final File channelsFile = temporaryFolder.newFile();
        Channel channel = createChannel("dev", "wildfly-channel", "http://test.test", "org.wildfly");
        MetadataTestUtils.writeChannels(channelsFile.toPath(), List.of(channel));

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.CHANNELS, channelsFile.getAbsolutePath(),
                CliConstants.DEFINITION, provisionDefinitionFile.getAbsolutePath());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(configCaptor.capture(), channelCaptor.capture(), any());
        assertThat(configCaptor.getValue().getFeaturePackDeps())
                .map(fp->fp.getLocation().getProducerName())
                .containsExactly("org.wildfly.core:wildfly-core-galleon-pack::zip");
        assertThat(channelCaptor.getValue())
                .flatMap(Channel::getRepositories)
                .map(Repository::getId)
                .containsExactly("dev");
    }

    @Test
    public void fplAndDefinitionAreNotAllowedTogether() throws Exception {
        final File provisionDefinitionFile = temporaryFolder.newFile("provision.xml");
        final File channelsFile = temporaryFolder.newFile();

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.DEFINITION, provisionDefinitionFile.getAbsolutePath(),
                CliConstants.CHANNELS, channelsFile.getAbsolutePath(),
                CliConstants.FPL, "test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
    }

    @Test
    public void provisionConfigAndChannelSet() throws IOException {
        final File channelsFile = temporaryFolder.newFile();

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.CHANNELS, channelsFile.getAbsolutePath(),
                CliConstants.CHANNEL_MANIFEST, "g:a:v",
                CliConstants.FPL, "test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES
                .exclusiveOptions(CliConstants.CHANNELS, CliConstants.CHANNEL_MANIFEST).getMessage()));
    }

    @Test
    public void provisionConfigAndRemoteRepoSet() throws Exception {
        Path channelsFile = temporaryFolder.newFile().toPath();
        MetadataTestUtils.prepareChannel(channelsFile, List.of(new URL("file:some-manifest.yaml")));

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.CHANNELS, channelsFile.toString(),
                CliConstants.REPOSITORIES, "file:/test",
                CliConstants.FPL, "g:a");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(configCaptor.capture(), channelCaptor.capture(), any());
        assertThat(channelCaptor.getValue().get(0).getRepositories()
                .stream().map(Repository::getUrl).collect(Collectors.toList()))
                .containsOnly("file:/test");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void passShadowRepositories() throws Exception {
        Path channelsFile = temporaryFolder.newFile().toPath();
        MetadataTestUtils.prepareChannel(channelsFile, List.of(new URL("file:some-manifest.yaml")));

        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.CHANNELS, channelsFile.toString(),
                CliConstants.SHADE_REPOSITORIES, "file:/test",
                CliConstants.FPL, "g:a");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        final ArgumentCaptor<List<Repository>> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(provisionAction).provision(configCaptor.capture(), channelCaptor.capture(), listArgumentCaptor.capture());
        assertThat(listArgumentCaptor.getValue())
                .map(Repository::getUrl)
                .contains("file:/test");
    }

    @Override
    protected MavenOptions getCapturedMavenOptions() throws Exception {
        Mockito.verify(actionFactory).install(any(), mavenOptions.capture(), any());
        return mavenOptions.getValue();
    }

    @Override
    protected String[] getDefaultArguments() {
        return new String[]{CliConstants.Commands.INSTALL, CliConstants.DIR, "test",
                CliConstants.PROFILE, KNOWN_FPL};
    }

    private static Channel createChannel(String test, String test1, String url, String groupId) {
        Channel channel = new Channel("", "", null,
                List.of(new Repository(test, url)),
                new ChannelManifestCoordinate(groupId, test1),
                null, null);
        return channel;
    }
}
