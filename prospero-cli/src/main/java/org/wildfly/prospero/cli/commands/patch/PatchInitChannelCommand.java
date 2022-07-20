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
import java.util.Optional;

@CommandLine.Command(
        name = CliConstants.PATCH_INIT_CHANNEL,
        sortOptions = false
)
public class PatchInitChannelCommand extends AbstractCommand {
    public static final String PATCHES_REPO_ID = "patches-repository";
    @CommandLine.Option(
            names = CliConstants.PATCH_CHANNEL_NAME
    )
    private String name;

    @CommandLine.Option(
            names = CliConstants.PATCH_REPOSITORY_URL
    )
    private URL url;

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;


    public PatchInitChannelCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        Path installationDirectory = determineInstallationDirectory(directory);
        MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory);

        if (!validateRepository(metadataAction) || !validateChannel()) {
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        if (url.getProtocol().equals("file")) {
            if (!createLocalRepository()) {
                return ReturnCodes.PROCESSING_ERROR;
            }
        }

        // add new channel
        metadataAction.addChannel(name);

        // add new repository
        metadataAction.addRepository(PATCHES_REPO_ID, url);
        console.println(CliMessages.MESSAGES.repositoryAdded(PATCHES_REPO_ID));

        // if the url is local folder, make sure it's created

        return ReturnCodes.SUCCESS;
    }

    private boolean createLocalRepository() throws URISyntaxException {
        final File file = new File(url.toURI());
        if (!isWritable(file)) {
            console.println(CliMessages.MESSAGES.unableToCreateLocalRepository(url));
            return false;
        }
        if (file.exists() && !Files.isDirectory(file.toPath())) {
            console.println(CliMessages.MESSAGES.repositoryIsNotDirectory(url));
            return false;
        }

        if (!file.exists()) {
            try {
                Files.createDirectories(file.toPath());
            } catch (IOException e) {
                console.error(CliMessages.MESSAGES.unableToCreateLocalRepository(url));
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
        try {
            final ChannelRef channelRef = ChannelRef.fromString(name);
            if (channelRef.getGav() == null) {
                console.error(CliMessages.MESSAGES.illegalChannel(name));
                return false;
            }
        } catch (IllegalArgumentException e) {
            console.error(CliMessages.MESSAGES.illegalChannel(name));
            return false;
        }
        return true;
    }

    private boolean validateRepository(MetadataAction metadataAction) throws MetadataException {
        if (metadataAction.getRepositories().stream().anyMatch(r->r.getId().equals(PATCHES_REPO_ID))) {
            console.error(CliMessages.MESSAGES.patchesRepoExist(PATCHES_REPO_ID));
            return false;
        }
        return true;
    }
}
