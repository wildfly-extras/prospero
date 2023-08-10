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

package org.wildfly.prospero.cli.commands.channel;

import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.maven.ChannelCoordinate;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.api.ArtifactUtils;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.ArgumentParsingException;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.AbstractInstallCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@CommandLine.Command(name = CliConstants.Commands.ADD, sortOptions = false)
public class ChannelAddCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.CHANNEL_NAME)
    private String channelName;

    @CommandLine.Option(names = CliConstants.CHANNEL_MANIFEST)
    private String gavUrlOrPath;

    @CommandLine.Option(names = CliConstants.DIR)
    private Optional<Path> directory;

    @CommandLine.Option(names = CliConstants.REPOSITORIES, split = ",", paramLabel = CliConstants.REPO_URL)
    private List<String> repositoryDefs;

    @CommandLine.Option(names = CliConstants.CHANNEL)
    private Optional<String> channelUrlOrPath;

    public ChannelAddCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        Path installationDirectory = determineInstallationDirectory(directory);
        List<Repository> repositories;
        List<Channel> channels = null;
        if(channelUrlOrPath.isPresent()) {
            final ChannelCoordinate coord = ArtifactUtils.channelCoordFromString(channelUrlOrPath.get());
            checkFileExists(coord.getUrl(), channelUrlOrPath.get());
            ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
                    .setChannelCoordinates(channelUrlOrPath.get())
                    .build();
            channels = AbstractInstallCommand.resolveChannels(provisioningDefinition,
                    MavenOptions.DEFAULT_OPTIONS);
        } else if (!(gavUrlOrPath.isEmpty() || repositoryDefs.isEmpty())) {
            repositories = RepositoryDefinition.from(repositoryDefs);

            console.println(CliMessages.MESSAGES.subscribeChannel(installationDirectory, channelName));
            ChannelManifestCoordinate manifest = ArtifactUtils.manifestCoordFromString(gavUrlOrPath);
                channels = List.of(new Channel(channelName, null, null, repositories, manifest, null, null));
            } else {
                return ReturnCodes.INVALID_ARGUMENTS;
            }
            try (final MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory)) {
                for (Channel channel : channels) {
                    metadataAction.addChannel(channel);
                }
            }

            console.println(CliMessages.MESSAGES.channelAdded(channelName));
            return ReturnCodes.SUCCESS;
        }

        private void checkFileExists(URL resourceUrl, String argValue) throws ArgumentParsingException {
            if (resourceUrl != null) {
                try (InputStream is = resourceUrl.openStream()) {
                    // OK ignore, just need to check it exists
                } catch (IOException e) {
                    throw CliMessages.MESSAGES.missingRequiresResource(argValue);
                }
            }
        }
    }