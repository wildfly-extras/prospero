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

import org.apache.commons.lang3.RandomStringUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.api.ArtifactUtils;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@CommandLine.Command(
        name = CliConstants.Commands.CUSTOMIZATION_INITIALIZE_CHANNEL,
        aliases = {CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL},
        sortOptions = false
)
public class ChannelInitializeCommand extends AbstractCommand {
    public static final String CUSTOMIZATION_REPO_ID = "customization-repository";
    public static final String CUSTOM_CHANNELS_GROUP_ID = "custom.channels";
    public static final String DEFAULT_CUSTOMIZATION_REPOSITORY = "customization-repository";
    @CommandLine.Option(
            names = CliConstants.CHANNEL_MANIFEST
    )
    private Optional<String> manifestName;

    @CommandLine.Option(
            names = CliConstants.CUSTOMIZATION_REPOSITORY_URL,
            descriptionKey = "customization-repository"
    )
    private Optional<URL> repositoryUrl;

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;


    public ChannelInitializeCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        Path installationDirectory = determineInstallationDirectory(directory);
        try (MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory)) {

            if (!validateRepository(metadataAction) || !validateChannel()) {
                return ReturnCodes.INVALID_ARGUMENTS;
            }

            final URL url;
            if (repositoryUrl.isPresent()) {
                url = repositoryUrl.get();
                if (repositoryUrl.get().getProtocol().equals("file")) {
                    if (!createLocalRepository(Paths.get(repositoryUrl.get().toURI()))) {
                        return ReturnCodes.PROCESSING_ERROR;
                    }
                }
            } else {
                final Path defaultLocalRepoPath = installationDirectory.resolve(InstallationMetadata.METADATA_DIR)
                        .resolve(DEFAULT_CUSTOMIZATION_REPOSITORY);
                if (!createLocalRepository(defaultLocalRepoPath)) {
                    return ReturnCodes.PROCESSING_ERROR;
                }
                url = defaultLocalRepoPath.toUri().toURL();
            }

            if (manifestName.isEmpty()) {
                if (customizationChannelExists(metadataAction)) {
                    console.error(CliMessages.MESSAGES.customizationChannelAlreadyExists());
                    return ReturnCodes.PROCESSING_ERROR;
                }
                manifestName = Optional.of(CUSTOM_CHANNELS_GROUP_ID + ":" + RandomStringUtils.randomAlphanumeric(8));
            }

            // add new channel
            console.println(CliMessages.MESSAGES.registeringCustomChannel(manifestName.get()));
            Channel channel = new Channel("customization", null, null, null,
                    List.of(new Repository(CUSTOMIZATION_REPO_ID, url.toExternalForm())),
                    ArtifactUtils.manifestFromString(manifestName.get()));
            metadataAction.addChannel(channel);
        }

        return ReturnCodes.SUCCESS;
    }

    private boolean customizationChannelExists(MetadataAction metadataAction) throws MetadataException {
        return metadataAction.getChannels().stream()
                .map(Channel::getManifestRef)
                .anyMatch(m -> m.getGav() != null && m.getGav().startsWith(CUSTOM_CHANNELS_GROUP_ID + ":"));
    }

    private boolean createLocalRepository(Path localRepository) throws URISyntaxException {
        final File file = localRepository.toFile();
        if (!isWritable(file)) {
            console.error(CliMessages.MESSAGES.unableToCreateLocalRepository(localRepository));
            return false;
        }
        if (file.exists() && !Files.isDirectory(file.toPath())) {
            console.error(CliMessages.MESSAGES.repositoryIsNotDirectory(localRepository));
            return false;
        }

        if (!file.exists()) {
            try {
                Files.createDirectories(file.toPath());
            } catch (IOException e) {
                console.error(CliMessages.MESSAGES.unableToCreateLocalRepository(localRepository));
                console.error(e.getLocalizedMessage());
                return false;
            }
        }
        return true;
    }

    private boolean isWritable(File file) {
        if (file.exists()) {
            return Files.isWritable(file.toPath());
        } else if (file.getParentFile() == null) {
            return false;
        } else {
            return isWritable(file.getParentFile());
        }
    }

    private boolean validateChannel() {
        if (manifestName.isEmpty()) {
            return true;
        }
        try {
            final ChannelManifestCoordinate manifestRef = ArtifactUtils.manifestFromString(manifestName.get());
            if (manifestRef.getGav() == null) {
                console.error(CliMessages.MESSAGES.illegalChannel(manifestName.get()));
                return false;
            }
        } catch (IllegalArgumentException e) {
            console.error(CliMessages.MESSAGES.illegalChannel(manifestName.get()));
            return false;
        }
        return true;
    }

    private boolean validateRepository(MetadataAction metadataAction) throws MetadataException {
        if (metadataAction.getChannels().stream().flatMap(c->c.getRepositories().stream()).anyMatch(r->r.getId().equals(CUSTOMIZATION_REPO_ID))) {
            console.error(CliMessages.MESSAGES.customizationRepoExist(CUSTOMIZATION_REPO_ID));
            return false;
        }
        return true;
    }
}
