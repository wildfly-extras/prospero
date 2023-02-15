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

import org.wildfly.channel.Repository;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.Commands.REVERT,
        sortOptions = false
)
public class RevertCommand extends AbstractParentCommand {

    @CommandLine.Command(name = CliConstants.Commands.PERFORM, sortOptions = false)
    public static class PerformCommand extends AbstractMavenCommand {

        @CommandLine.Option(names = CliConstants.REVISION, required = true)
        String revision;

        public PerformCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final Path installationDirectory = determineInstallationDirectory(directory);
            final MavenOptions mavenOptions = parseMavenOptions();

            final List<Repository> overrideRepositories = RepositoryDefinition.from(temporaryRepositories);

            InstallationHistoryAction historyAction = actionFactory.history(installationDirectory, console);
            historyAction.rollback(new SavedState(revision), mavenOptions, overrideRepositories);
            return ReturnCodes.SUCCESS_LOCAL_CHANGES;
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.APPLY, sortOptions = false)
    public static class ApplyCommand extends AbstractCommand {

        @CommandLine.Option(names = CliConstants.DIR)
        Optional<Path> directory;

        @CommandLine.Option(names = CliConstants.UPDATE_DIR, required = true)
        Path updateDirectory;

        public ApplyCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final Path installationDirectory = determineInstallationDirectory(directory);

            InstallationHistoryAction historyAction = actionFactory.history(installationDirectory, console);
            historyAction.applyRevert(updateDirectory.toAbsolutePath());
            return ReturnCodes.SUCCESS_LOCAL_CHANGES;
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.PREPARE, sortOptions = false)
    public static class PrepareCommand extends AbstractMavenCommand {

        @CommandLine.Option(names = CliConstants.REVISION, required = true)
        String revision;

        @CommandLine.Option(names = CliConstants.UPDATE_DIR, required = true)
        Path updateDirectory;

        public PrepareCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            verifyTargetDirectoryIsEmpty(updateDirectory);

            final Path installationDirectory = determineInstallationDirectory(directory);
            final MavenOptions mavenOptions = parseMavenOptions();

            final List<Repository> overrideRepositories = RepositoryDefinition.from(temporaryRepositories);

            InstallationHistoryAction historyAction = actionFactory.history(installationDirectory, console);
            historyAction.prepareRevert(new SavedState(revision), mavenOptions, overrideRepositories, updateDirectory.toAbsolutePath());
            return ReturnCodes.SUCCESS_LOCAL_CHANGES;
        }
    }

    public RevertCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory, CliConstants.Commands.REVERT, List.of(
                new PrepareCommand(console, actionFactory),
                new ApplyCommand(console, actionFactory),
                new PerformCommand(console, actionFactory)
        ));
    }
}
