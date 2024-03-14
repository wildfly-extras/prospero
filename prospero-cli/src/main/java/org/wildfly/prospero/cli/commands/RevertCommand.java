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
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.actions.ApplyCandidateAction;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.FileConflict;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.RepositoryUtils;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.InvalidUpdateCandidateException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.FileConflictPrinter;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.api.TemporaryFilesManager;
import picocli.CommandLine;

import static org.wildfly.prospero.cli.ReturnCodes.SUCCESS;

@CommandLine.Command(
        name = CliConstants.Commands.REVERT,
        sortOptions = false
)
public class RevertCommand extends AbstractParentCommand {

    private static int applyCandidate(CliConsole console, ApplyCandidateAction applyCandidateAction, boolean yes) throws OperationException, ProvisioningException {
        console.changesFound(applyCandidateAction.findUpdates().getArtifactUpdates());
        final List<FileConflict> conflicts = applyCandidateAction.getConflicts();
        FileConflictPrinter.print(conflicts, console);

        if (!yes && !console.confirm(CliMessages.MESSAGES.continueWithRevert(),
                CliMessages.MESSAGES.applyingChanges(), CliMessages.MESSAGES.revertCancelled())) {
            return SUCCESS;
        }

        applyCandidateAction.applyUpdate(ApplyCandidateAction.Type.REVERT);

        console.println("");
        console.println(CliMessages.MESSAGES.revertComplete(applyCandidateAction.getCandidateRevision().getName()));
        return SUCCESS;
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
            final long startTime = System.currentTimeMillis();
            final Path installationDirectory = determineInstallationDirectory(directory);
            final MavenOptions mavenOptions = parseMavenOptions();

            final List<Repository> repositories = RepositoryDefinition.from(temporaryRepositories);
            try (TemporaryFilesManager temporaryFiles = TemporaryFilesManager.getInstance()) {
                final List<Repository> overrideRepositories = RepositoryUtils.unzipArchives(repositories, temporaryFiles);

                InstallationHistoryAction historyAction = actionFactory.history(installationDirectory, console);

                console.println(CliMessages.MESSAGES.revertStart(installationDirectory, revision));
                console.println("");

                final Path tempDirectory = temporaryFiles.createTempDirectory("revert-candidate");
                historyAction.prepareRevert(new SavedState(revision), mavenOptions, overrideRepositories, tempDirectory);

                console.println("");
                console.println(CliMessages.MESSAGES.comparingChanges());

                final ApplyCandidateAction applyCandidateAction = actionFactory.applyUpdate(installationDirectory, tempDirectory);

                validateRevertCandidate(installationDirectory, tempDirectory, applyCandidateAction);

                applyCandidate(console, applyCandidateAction, yes);
            } catch (IOException e) {
                throw ProsperoLogger.ROOT_LOGGER.unableToCreateTemporaryDirectory(e);
            }

            final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
            console.println(CliMessages.MESSAGES.operationCompleted(totalTime));
            return SUCCESS;
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.APPLY, sortOptions = false)
    public static class ApplyCommand extends AbstractCommand {

        @CommandLine.Option(names = CliConstants.DIR)
        Optional<Path> directory;

        @CommandLine.Option(names = CliConstants.CANDIDATE_DIR, required = true)
        Path candidateDirectory;

        @CommandLine.Option(names = CliConstants.REMOVE)
        boolean remove;

        @CommandLine.Option(names = {CliConstants.Y, CliConstants.YES})
        boolean yes;

        public ApplyCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final long startTime = System.currentTimeMillis();
            final Path installationDirectory = determineInstallationDirectory(directory);
            final ApplyCandidateAction applyCandidateAction = actionFactory.applyUpdate(installationDirectory, candidateDirectory.toAbsolutePath());

            validateRevertCandidate(installationDirectory, candidateDirectory, applyCandidateAction);

            console.println(CliMessages.MESSAGES.revertStart(installationDirectory, applyCandidateAction.getCandidateRevision().getName()));
            console.println("");

            applyCandidate(console, applyCandidateAction, yes);
            final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
            console.println(CliMessages.MESSAGES.operationCompleted(totalTime));
            applyCandidateAction.removeUpdateCandidate(remove);
            return SUCCESS;
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.PREPARE, sortOptions = false)
    public static class PrepareCommand extends AbstractMavenCommand {

        @CommandLine.Option(names = CliConstants.REVISION, required = true)
        String revision;

        @CommandLine.Option(names = CliConstants.CANDIDATE_DIR, required = true)
        Path candidateDirectory;

        public PrepareCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final long startTime = System.currentTimeMillis();
            verifyTargetDirectoryIsEmpty(candidateDirectory);

            final Path installationDirectory = determineInstallationDirectory(directory);
            final MavenOptions mavenOptions = parseMavenOptions();

            try(TemporaryFilesManager temporaryFiles = TemporaryFilesManager.getInstance()) {
                final List<Repository> repositories = RepositoryDefinition.from(temporaryRepositories);
                final List<Repository> overrideRepositories = RepositoryUtils.unzipArchives(repositories, temporaryFiles);

                console.println(CliMessages.MESSAGES.buildRevertCandidateHeader(installationDirectory));

                InstallationHistoryAction historyAction = actionFactory.history(installationDirectory, console);
                historyAction.prepareRevert(new SavedState(revision), mavenOptions, overrideRepositories, candidateDirectory.toAbsolutePath());
            }

            console.println("");
            console.println(CliMessages.MESSAGES.revertCandidateGenerated(candidateDirectory));
            final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
            console.println(CliMessages.MESSAGES.operationCompleted(totalTime));
            return SUCCESS;

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
