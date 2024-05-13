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
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.Repository;
import org.wildfly.channel.InvalidChannelMetadataException;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.api.ArtifactUtils;
import org.wildfly.prospero.api.RepositoryUtils;
import org.wildfly.prospero.api.TemporaryFilesManager;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@CommandLine.Command(name = CliConstants.Commands.ADD, sortOptions = false)
public class ChannelAddCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.CHANNEL_NAME, required = true)
    private String channelName;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    ChannelParamsGroup channelOptions;

    @CommandLine.Option(names = CliConstants.DIR)
    private Optional<Path> directory;

    static class ChannelParamsGroup {
        @CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
        private ChannelGroup channelGroup;

        @CommandLine.Option(names = CliConstants.CHANNEL)
        private Path channelLocation;
    }

    static class ChannelGroup {
        @CommandLine.Option(names = CliConstants.CHANNEL_MANIFEST, required = true)
        private String manifestLocation;

        @CommandLine.Option(names = CliConstants.REPOSITORIES, split = ",", paramLabel = CliConstants.REPO_URL, required = true)
        private List<String> repositoryDefs;
    }

    public ChannelAddCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        Path installationDirectory = determineInstallationDirectory(directory);
        List<Channel> channels = null;
        if (channelOptions.channelLocation != null) {
            try{
                channels = ChannelMapper.fromString(Files.readString(Path.of(channelOptions.channelLocation.toString())));
            }catch (InvalidChannelMetadataException e){
                    throw CliMessages.MESSAGES.invalidManifest(e.getMessage());
            }catch (NoSuchFileException e){
                throw CliMessages.MESSAGES.fileNotExists(e.getMessage());
            }

            Channel channel = channels.get(0);
            channels.set(0, new Channel(channelName, channel.getDescription(), channel.getVendor(), channel.getRepositories(), channel.getManifestCoordinate(), channel.getBlocklistCoordinate(), channel.getNoStreamStrategy()));
            if (channels.size() > 1) {
                throw new RuntimeException(CliMessages.MESSAGES.sizeOfChannel());
            }
        } else if (!(channelOptions.channelGroup.manifestLocation.isEmpty() || channelOptions.channelGroup.repositoryDefs.isEmpty())) {
            console.println(CliMessages.MESSAGES.subscribeChannel(installationDirectory, channelName));

            ChannelManifestCoordinate manifest = ArtifactUtils.manifestCoordFromString(channelOptions.channelGroup.manifestLocation);
            try (TemporaryFilesManager temporaryFiles = TemporaryFilesManager.getInstance()) {
                final List<Repository> repositories = RepositoryUtils.unzipArchives(RepositoryDefinition.from(channelOptions.channelGroup.repositoryDefs), temporaryFiles);
                channels = List.of(new Channel(channelName, null, null, repositories, manifest, null, null));

            }
        } else {
            throw new RuntimeException(CliMessages.MESSAGES.invalidArgument());
        }
        try (MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory)) {
            Channel channel = channels.get(0);
                metadataAction.addChannel(channel);

        }
        console.println(CliMessages.MESSAGES.channelAdded(channelName));
        return ReturnCodes.SUCCESS;

    }
}
