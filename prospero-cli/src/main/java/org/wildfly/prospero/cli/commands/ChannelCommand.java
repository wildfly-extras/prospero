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

package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.model.ChannelRef;
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
            MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory);
            List<ChannelRef> channels = metadataAction.getChannels();

            for (ChannelRef channel: channels) {
                console.println(channel.getGavOrUrlString());
            }

            return ReturnCodes.SUCCESS;
        }
    }

}
