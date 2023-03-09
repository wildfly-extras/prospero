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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.actions.ApplyCandidateAction;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.FileConflict;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.InvalidUpdateCandidateException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.FileConflictPrinter;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.Commands.REVERT,
        sortOptions = false
)
public class RevertCommand extends AbstractParentCommand {

    private static int applyCandidate(CliConsole console, ApplyCandidateAction applyCandidateAction, boolean yes) throws OperationException, ProvisioningException {
        console.updatesFound(applyCandidateAction.findUpdates().getArtifactUpdates());
        final List<FileConflict> conflicts = applyCandidateAction.getConflicts();
        FileConflictPrinter.print(conflicts, console);

        if (!yes && !console.confirm(CliMessages.MESSAGES.continueWithUpdate(), "", CliMessages.MESSAGES.updateCancelled())) {
            return ReturnCodes.SUCCESS;
        }

        applyCandidateAction.applyUpdate(ApplyCandidateAction.Type.REVERT);
        return ReturnCodes.SUCCESS;
    }

    private static void validateRevertCandidate(Path installationDirectory, Path updateDirectory, ApplyCandidateAction applyCandidateAction) throws InvalidUpdateCandidateException, MetadataException {
        final ApplyCandidateAction.ValidationResult result = applyCandidateAction.verifyCandidate(ApplyCandidateAction.Type.REVERT);
        if (ApplyCandidateAction.ValidationResult.STALE == result) {
            throw CliMessages.MESSAGES.updateCandidateStateNotMatched(installationDirectory, updateDirectory.toAbsolutePath());
        } else if (ApplyCandidateAction.ValidationResult.WRONG_TYPE == result) {
            throw CliMessages.MESSAGES.updateCandidateWrongType(installationDirectory, ApplyCandidateAction.Type.REVERT);
        } else if (ApplyCandidateAction.ValidationResult.NOT_CANDIDATE == result) {
            throw CliMessages.MESSAGES.notCandidate(updateDirectory.toAbsolutePath());
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.PERFORM, sortOptions = false)
    public static class PerformCommand extends AbstractMavenCommand {

        @CommandLine.Option(names = CliConstants.REVISION, required = true)
        String revision;

        @CommandLine.Option(names = {CliConstants.Y, CliConstants.YES})
        boolean yes;

        public PerformCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final Path installationDirectory = determineInstallationDirectory(directory);
            final MavenOptions mavenOptions = parseMavenOptions();

            final List<Repository> overrideRepositories = RepositoryDefinition.from(temporaryRepositories);

            InstallationHistoryAction historyAction = actionFactory.history(installationDirectory, console);
            Path tempDirectory = null;
            try {
                tempDirectory = Files.createTempDirectory("revert-candidate");
                historyAction.prepareRevert(new SavedState(revision), mavenOptions, overrideRepositories, tempDirectory);
                final ApplyCandidateAction applyCandidateAction = actionFactory.applyUpdate(installationDirectory, tempDirectory);

                validateRevertCandidate(installationDirectory, tempDirectory, applyCandidateAction);

                return applyCandidate(console, applyCandidateAction, yes);
            } catch (IOException e) {
                throw Messages.MESSAGES.unableToCreateTemporaryDirectory(e);
            } finally {
                if (tempDirectory != null) {
                    FileUtils.deleteQuietly(tempDirectory.toFile());
                }
            }
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.APPLY, sortOptions = false)
    public static class ApplyCommand extends AbstractCommand {

        @CommandLine.Option(names = CliConstants.DIR)
        Optional<Path> directory;

        @CommandLine.Option(names = CliConstants.UPDATE_DIR, required = true)
        Path updateDirectory;

        @CommandLine.Option(names = {CliConstants.Y, CliConstants.YES})
        boolean yes;

        public ApplyCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final Path installationDirectory = determineInstallationDirectory(directory);
            final ApplyCandidateAction applyCandidateAction = actionFactory.applyUpdate(installationDirectory, updateDirectory.toAbsolutePath());

            validateRevertCandidate(installationDirectory, updateDirectory, applyCandidateAction);

            return applyCandidate(console, applyCandidateAction, yes);
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
            return ReturnCodes.SUCCESS;
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
