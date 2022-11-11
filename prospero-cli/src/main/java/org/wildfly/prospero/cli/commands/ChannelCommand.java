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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

@CommandLine.Command(name = CliConstants.Commands.CHANNEL)
public class ChannelCommand extends AbstractCommand {

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    public ChannelCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(console.getErrOut());
        return ReturnCodes.INVALID_ARGUMENTS;
    }

    @CommandLine.Command(name = CliConstants.Commands.LIST)
    public static class ChannelListCommand extends AbstractCommand {

        @CommandLine.Option(names = CliConstants.DIR)
        Optional<Path> directory;

        public ChannelListCommand(Console console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            Path installationDirectory = determineInstallationDirectory(directory);
            List<Channel> channels;
            try (MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory)) {
                channels = metadataAction.getChannels();
            }

            console.println("-------");
            for (Channel channel: channels) {
                console.println("#" + channel.getName());
                final String manifest = channel.getManifestRef().getGav() == null
                        ?channel.getManifestRef().getUrl().toExternalForm():channel.getManifestRef().getGav();
                console.println("  " + "manifest: " + manifest);
                console.println("  " + "repositories:");
                for (Repository repository : channel.getRepositories()) {
                    console.println("  " + "  " + "id: " + repository.getId());
                    console.println("  " + "  " + "url: " + repository.getUrl());
                }
                console.println("-------");
            }

            return ReturnCodes.SUCCESS;
        }
    }

}
