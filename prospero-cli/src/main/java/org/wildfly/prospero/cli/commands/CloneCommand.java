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

import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.options.LocalRepoOptions;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CommandLine.Command(name = CliConstants.Commands.CLONE)
public class CloneCommand extends AbstractCommand {

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    public CloneCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    public void addSubCommands(CommandLine rootCmd) {
        CommandLine cloneCmd = rootCmd.getSubcommands().get(CliConstants.Commands.CLONE);
        cloneCmd.addSubcommand(new CloneExportCommand(console, actionFactory))
          .addSubcommand(new CloneRestoreCommand(console, actionFactory));
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(console.getErrOut());
        return ReturnCodes.INVALID_ARGUMENTS;
    }

    @CommandLine.Command(name = CliConstants.Commands.EXPORT)
    private static class CloneExportCommand extends AbstractCommand {

        @CommandLine.Option(names = CliConstants.DIR, order = 1)
        Optional<Path> directory;

        @CommandLine.Option(names = CliConstants.ARG_PATH, required = true, paramLabel = CliConstants.PATH, order = 2)
        Path outPath;

        CloneExportCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            if (Files.exists(outPath)) {
                throw Messages.MESSAGES.outFileExists(outPath);
            }
            actionFactory
              .exportAction(determineInstallationDirectory(directory))
              .export(outPath);
            console.println(Messages.MESSAGES.installationExported(outPath));
            return ReturnCodes.SUCCESS_LOCAL_CHANGES;
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.RESTORE)
    private static class CloneRestoreCommand extends AbstractCommand {

        @CommandLine.Option(names = CliConstants.DIR, order = 1)
        Optional<Path> directory;

        @CommandLine.Option(names = CliConstants.ARG_PATH, required = true, paramLabel = CliConstants.PATH, order = 2)
        Path inPath;

        @CommandLine.Option(
          names = CliConstants.REPOSITORIES, paramLabel = CliConstants.REPO_URL, split = ",", order = 3
        )
        List<String> remoteRepositories = new ArrayList<>();

        @CommandLine.ArgGroup(headingKey = "localRepoOptions.heading", order = 4)
        LocalRepoOptions localRepoOptions = new LocalRepoOptions();

        @CommandLine.Option(names = CliConstants.OFFLINE, order = 5)
        Optional<Boolean> offline = Optional.empty();

        CloneRestoreCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            if (Files.notExists(inPath)) {
                console.println(Messages.MESSAGES.restoreFileNotExisted(inPath));
                return ReturnCodes.INVALID_ARGUMENTS;
            }

            final MavenOptions.Builder mavenOptions = localRepoOptions.toOptions();
            offline.map(mavenOptions::setOffline);
            Path installationDirectory = directory.orElse(currentDir()).toAbsolutePath();
            actionFactory
              .restoreAction(installationDirectory, mavenOptions.build(), console)
              .restore(inPath, RepositoryDefinition.from(remoteRepositories));
            console.println(Messages.MESSAGES.installationMetaRestored(inPath, installationDirectory));
            return ReturnCodes.SUCCESS_LOCAL_CHANGES;
        }
    }
}
