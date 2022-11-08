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

import org.apache.commons.lang3.StringUtils;
import org.wildfly.channel.Channel;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Optional;

@CommandLine.Command(name = CliConstants.Commands.REMOVE)
public class ChannelRemoveCommand extends AbstractCommand {

    @CommandLine.Parameters(index = "0", paramLabel = "channel-index")
    int index;

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    public ChannelRemoveCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        Path installationDirectory = determineInstallationDirectory(directory);
        try (final MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory)) {
            final Channel channel = metadataAction.getChannel(index);
            if (channel != null) {
                metadataAction.removeChannel(channel.getName());
                final String name;
                if (StringUtils.isNotEmpty(channel.getManifestRef().getGav())) {
                    name = channel.getManifestRef().getGav();
                } else {
                    name = channel.getManifestRef().getUrl().toExternalForm();
                }
                console.println(CliMessages.MESSAGES.channelRemoved(name));
                return ReturnCodes.SUCCESS;
            } else {
                console.println("The requested channel doesn't exist");
                return ReturnCodes.INVALID_ARGUMENTS;
            }
        }
    }
}
