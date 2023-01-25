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
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.api.ArtifactUtils;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import picocli.CommandLine;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

import static org.wildfly.prospero.cli.commands.channel.ChannelInitializeCommand.CUSTOM_CHANNELS_GROUP_ID;
import static org.wildfly.prospero.cli.commands.channel.ChannelInitializeCommand.CUSTOMIZATION_REPO_ID;

@CommandLine.Command(
        name = CliConstants.Commands.CUSTOMIZATION_PROMOTE,
        sortOptions = false
)
public class ChannelPromoteCommand extends AbstractCommand {
    @CommandLine.Option(
            names = CliConstants.CHANNEL_MANIFEST
    )
    private Optional<String> name;

    @CommandLine.Option(
            names = CliConstants.CUSTOMIZATION_ARCHIVE,
            required = true
    )
    private Path archive;

    @CommandLine.Option(
            names = CliConstants.CUSTOMIZATION_REPOSITORY_URL,
            descriptionKey = "target-repository-url"
    )
    private Optional<URL> url;

    @CommandLine.Option(
            names = CliConstants.DIR
    )
    private Optional<Path> directory;

    @CommandLine.Option(
            names = {CliConstants.Y, CliConstants.YES}
    )
    private boolean noPrompt;


    public ChannelPromoteCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        if (url.isEmpty()) {
            final Optional<URL> res = readSetting(a->a.getChannels().stream()
                    .flatMap(c -> c.getRepositories().stream())
                    .filter(c -> c.getId().equals(CUSTOMIZATION_REPO_ID))
                    .map(r-> {
                        try {
                            return new URL(r.getUrl());
                        } catch (MalformedURLException e) {
                            throw Messages.MESSAGES.invalidUrl(r.getUrl(), e);
                        }
                    })
                    .findFirst());
            if (res.isPresent()) {
                this.url = res;
            } else {
                console.error(CliMessages.MESSAGES.noCustomizationConfigFound(CliConstants.CHANNEL_NAME, CliConstants.CUSTOMIZATION_REPOSITORY_URL));
                return ReturnCodes.INVALID_ARGUMENTS;
            }
        }

        if (name.isEmpty()) {
            final Optional<MavenCoordinate> res = readSetting(a->a.getChannels().stream()
                    .map(Channel::getManifestCoordinate)
                    .filter(m -> m.getMaven() != null && m.getMaven().getGroupId().equals(CUSTOM_CHANNELS_GROUP_ID))
                    .map(ChannelManifestCoordinate::getMaven)
                    .findFirst());
            if (res.isPresent()) {
                this.name = res.map(this::toGav);
            } else {
                console.error(CliMessages.MESSAGES.noCustomizationConfigFound(CliConstants.CHANNEL_NAME, CliConstants.CUSTOMIZATION_REPOSITORY_URL));
                return ReturnCodes.INVALID_ARGUMENTS;
            }
        }

        if (!isValidManifestCoordinate()) {
            console.error(CliMessages.MESSAGES.wrongChannelCoordinateFormat());
            return ReturnCodes.INVALID_ARGUMENTS;
        }
        // TODO: support remote repositories
        final ChannelManifestCoordinate coordinate = ArtifactUtils.manifestCoordFromString(name.get());

        final boolean accepted;
        if (!noPrompt) {
            accepted = console.confirm(CliMessages.MESSAGES.continuePromote(), CliMessages.MESSAGES.continuePromoteAccepted(),
                    CliMessages.MESSAGES.continuePromoteRejected());
        } else {
            accepted = true;
        }

        if (accepted) {
            actionFactory.promoter(console).promote(archive.normalize().toAbsolutePath(), url.get(), coordinate);
        }

        return ReturnCodes.SUCCESS;
    }

    private String toGav(MavenCoordinate coord) {
        final String ga = coord.getGroupId() + ":" + coord.getArtifactId();
        if (coord.getVersion() != null && !coord.getVersion().isEmpty()) {
            return ga + ":" + coord.getVersion();
        }
        return ga;
    }

    private <T> Optional<T> readSetting(ThrowableFunction<MetadataAction, Optional<T>> reader) throws MetadataException {
        try {
            final Path installation = determineInstallationDirectory(directory);
            // see if we can read the customization configuration
            final Optional<T> customChannel;
            try (final MetadataAction metadataAction = actionFactory.metadataActions(installation)) {
                customChannel = reader.apply(metadataAction);
            }
            if (customChannel.isPresent()) {
                return customChannel;
            } else {
                return Optional.empty();
            }
        } catch (IllegalArgumentException e) {
            // we're not using installation - expect URL and channel name to be present
            return Optional.empty();
        }
    }

    private boolean isValidManifestCoordinate() {
        return name.get() != null && !name.get().isEmpty() && ArtifactUtils.isValidCoordinate(name.get());
    }

    interface ThrowableFunction<T,R> {
        R apply(T arg) throws MetadataException;
    }
}
