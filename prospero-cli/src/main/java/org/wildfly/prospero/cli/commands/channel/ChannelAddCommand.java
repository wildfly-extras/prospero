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

import org.codehaus.plexus.util.StringUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.api.ArtifactUtils;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CommandLine.Command(name = CliConstants.Commands.ADD)
public class ChannelAddCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.MANIFEST, required = true)
    String gavOrUrl;

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    @CommandLine.Option(names = CliConstants.REPOSITORY, required = true)
    List<String> repository;

    public ChannelAddCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        Path installationDirectory = determineInstallationDirectory(directory);
        List<Repository> repositories = new ArrayList<>();
        for (String repoKey : repository) {
            if (StringUtils.isEmpty(repoKey)) {
                throw CliMessages.MESSAGES.invalidRepositoryDefinition(repoKey);
            }
            final String[] splitRepoKey = repoKey.split("::");
            if (splitRepoKey.length != 2) {
                throw CliMessages.MESSAGES.invalidRepositoryDefinition(repoKey);
            }
            repositories.add(new Repository(splitRepoKey[0], splitRepoKey[1]));
        }
        ChannelManifestCoordinate manifest = ArtifactUtils.manifestFromString(gavOrUrl);
        Channel channel = new Channel(null, null, null, null, repositories, manifest);
        try (final MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory)) {
            metadataAction.addChannel(channel);
        }
        console.println(CliMessages.MESSAGES.channelAdded(gavOrUrl));
        return ReturnCodes.SUCCESS;
    }
}
