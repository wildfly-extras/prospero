/**
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

package org.wildfly.prospero.cli.commands.channel;

import org.apache.commons.lang3.RandomStringUtils;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.model.ChannelRef;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            names = CliConstants.CUSTOMIZATION_CHANNEL_NAME
    )
    private Optional<String> name;

    @CommandLine.Option(
            names = CliConstants.CUSTOMIZATION_REPOSITORY_URL
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
        MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory);

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

        if (name.isEmpty()) {
            if (metadataAction.getChannels().stream().anyMatch(c->c.getGav()!=null && c.getGav().startsWith(CUSTOM_CHANNELS_GROUP_ID + ":"))) {
                console.error(CliMessages.MESSAGES.customizationChannelAlreadyExists());
                return ReturnCodes.PROCESSING_ERROR;
            }
            name = Optional.of(CUSTOM_CHANNELS_GROUP_ID + ":" + RandomStringUtils.randomAlphanumeric(8));
        }

        // add new channel
        console.println(CliMessages.MESSAGES.registeringCustomChannel(name.get()));
        metadataAction.addChannel(name.get());

        // add new repository
        console.println(CliMessages.MESSAGES.registeringCustomRepository(url.toString()));
        metadataAction.addRepository(CUSTOMIZATION_REPO_ID, url);
        console.println(CliMessages.MESSAGES.repositoryAdded(CUSTOMIZATION_REPO_ID));

        return ReturnCodes.SUCCESS;
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
        if (name.isEmpty()) {
            return true;
        }
        try {
            final ChannelRef channelRef = ChannelRef.fromString(name.get());
            if (channelRef.getGav() == null) {
                console.error(CliMessages.MESSAGES.illegalChannel(name.get()));
                return false;
            }
        } catch (IllegalArgumentException e) {
            console.error(CliMessages.MESSAGES.illegalChannel(name.get()));
            return false;
        }
        return true;
    }

    private boolean validateRepository(MetadataAction metadataAction) throws MetadataException {
        if (metadataAction.getRepositories().stream().anyMatch(r->r.getId().equals(CUSTOMIZATION_REPO_ID))) {
            console.error(CliMessages.MESSAGES.customizationRepoExist(CUSTOMIZATION_REPO_ID));
            return false;
        }
        return true;
    }
}
