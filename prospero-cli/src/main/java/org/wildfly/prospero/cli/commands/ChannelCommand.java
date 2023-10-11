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
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.printers.ChannelPrinter;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import picocli.CommandLine;

@CommandLine.Command(name = CliConstants.Commands.CHANNEL)
public class ChannelCommand extends AbstractCommand {

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    public ChannelCommand(CliConsole console, ActionFactory actionFactory) {
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

        public ChannelListCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            Path installationDirectory = determineInstallationDirectory(directory);

            console.println(CliMessages.MESSAGES.listChannels(installationDirectory));

            List<Channel> channels;
            try (MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory)) {
                channels = metadataAction.getChannels();
            }

            final ChannelPrinter channelPrinter = new ChannelPrinter(console);
            console.println("-------");
            for (Channel channel : channels) {
                channelPrinter.print(channel);
                console.println("-------");
            }

            return ReturnCodes.SUCCESS;
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.VERSIONS)
    public static class ChannelVersionCommand extends AbstractCommand {

        protected static final String PREFIX = "  * ";
        @CommandLine.Option(names = CliConstants.DIR)
        private Optional<Path> directory;

        public ChannelVersionCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final Path installationDir = determineInstallationDirectory(directory);

            console.println(CliMessages.MESSAGES.serverVersionsHeader());
            try (MetadataAction metadataAction = actionFactory.metadataActions(installationDir)) {
                final ManifestVersionRecord channelVersions = metadataAction.getChannelVersions();
                for (ManifestVersionRecord.MavenManifest mavenManifest : channelVersions.getMavenManifests()) {
                    if (mavenManifest.getDescription() != null) {
                        console.println(PREFIX + mavenManifest.getDescription());
                    } else {
                        console.println(PREFIX + buildManifestGav(mavenManifest));
                    }
                }
                for (ManifestVersionRecord.UrlManifest urlManifest : channelVersions.getUrlManifests()) {
                    if (urlManifest.getDescription() != null) {
                        console.println(PREFIX + urlManifest.getDescription());
                    } else {
                        console.println(PREFIX + String.format("%s [%s]", urlManifest.getUrl(), urlManifest.getHash()));
                    }
                }
            }
            return ReturnCodes.SUCCESS;
        }

        private static String buildManifestGav(ManifestVersionRecord.MavenManifest mavenManifest) {
            return String.format("%s:%s [%s]", mavenManifest.getGroupId(), mavenManifest.getArtifactId(), mavenManifest.getVersion());
        }
    }


}
