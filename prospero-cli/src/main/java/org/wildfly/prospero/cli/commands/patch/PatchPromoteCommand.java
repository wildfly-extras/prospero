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

package org.wildfly.prospero.cli.commands.patch;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.RepositoryRef;
import picocli.CommandLine;

import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

import static org.wildfly.prospero.cli.commands.patch.PatchInitChannelCommand.CUSTOM_CHANNELS_GROUP_ID;
import static org.wildfly.prospero.cli.commands.patch.PatchInitChannelCommand.PATCHES_REPO_ID;

@CommandLine.Command(
        name = "promote",
        sortOptions = false
)
public class PatchPromoteCommand extends AbstractCommand {
    @CommandLine.Option(
            names = CliConstants.PATCH_CHANNEL_NAME
    )
    private Optional<String> name;

    @CommandLine.Option(
            names = CliConstants.PATCH_ARCHIVE,
            required = true
    )
    private Path archive;

    @CommandLine.Option(
            names = CliConstants.PATCH_REPOSITORY_URL
    )
    private Optional<URL> url;

    @CommandLine.Option(
            names = CliConstants.DIR
    )
    private Optional<Path> directory;


    public PatchPromoteCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        try {
            final Path installation = determineInstallationDirectory(directory);
            // see if we can read the customization configuration
            final MetadataAction metadataAction = actionFactory.metadataActions(installation);
            final Optional<ChannelRef> customChannel = metadataAction.getChannels().stream()
                    .filter(c -> c.getGav() != null && c.getGav().startsWith(CUSTOM_CHANNELS_GROUP_ID + ":"))
                    .findFirst();
            final Optional<RepositoryRef> customRepo = metadataAction.getRepositories().stream()
                    .filter(r -> r.getId().equals(PATCHES_REPO_ID))
                    .findFirst();
            if (customChannel.isPresent() && customRepo.isPresent()) {
                name = Optional.of(customChannel.get().getGav());
                url = Optional.of(new URL(customRepo.get().getUrl()));
                console.println(CliMessages.MESSAGES.foundCustomizationConfig(name.get(), url.get()));
            } else {
                console.error(CliMessages.MESSAGES.noCustomizationConfigFound(CliConstants.PATCH_CHANNEL_NAME, CliConstants.PATCH_REPOSITORY_URL));
                return ReturnCodes.INVALID_ARGUMENTS;
            }
        } catch (IllegalArgumentException e) {
            // we're not using installation - expect URL and channel name to be present
            if (url.isEmpty()) {
                console.error(CliMessages.MESSAGES.missingParameter(CliConstants.PATCH_REPOSITORY_URL));
                return ReturnCodes.INVALID_ARGUMENTS;
            }
            if (name.isEmpty()) {
                console.error(CliMessages.MESSAGES.missingParameter(CliConstants.PATCH_CHANNEL_NAME));
                return ReturnCodes.INVALID_ARGUMENTS;
            }
        }

        if (!isValidChannelCoordinate()) {
            console.error(CliMessages.MESSAGES.wrongChannelCoordinateFormat());
            return ReturnCodes.INVALID_ARGUMENTS;
        }
        // TODO: support remote repositories
        final ChannelRef coordinate = ChannelRef.fromString(name.get());

        boolean accepted = console.confirm(CliMessages.MESSAGES.continuePromote(), CliMessages.MESSAGES.continuePromoteAccepted(),
                CliMessages.MESSAGES.continuePromoteRejected());
        if (accepted) {
            actionFactory.promoter(console).promote(archive.normalize().toAbsolutePath(), url.get(), coordinate);
        }

        return ReturnCodes.SUCCESS;
    }

    private boolean isValidChannelCoordinate() {
        return name.get() != null && !name.get().isEmpty() && ChannelRef.isValidCoordinate(name.get());
    }
}
