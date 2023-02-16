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
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.Logger;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.actions.ApplyCandidateAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.FileConflict;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ArgumentParsingException;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.FileConflictPrinter;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.updates.MarkerFile;
import org.wildfly.prospero.updates.UpdateSet;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.Commands.UPDATE,
        sortOptions = false
)
public class UpdateCommand extends AbstractParentCommand {

    private static final Logger log = Logger.getLogger(UpdateCommand.class);

    public static final String JBOSS_MODULE_PATH = "module.path";
    public static final String PROSPERO_FP_GA = "org.wildfly.prospero:prospero-standalone-galleon-pack";
    public static final String PROSPERO_FP_ZIP = PROSPERO_FP_GA + "::zip";

    @CommandLine.Command(name = CliConstants.Commands.PERFORM, sortOptions = false)
    public static class PerformCommand extends AbstractMavenCommand {

        @CommandLine.Option(names = CliConstants.SELF)
        boolean self;

        @CommandLine.Option(names = {CliConstants.Y, CliConstants.YES})
        boolean yes;

        public PerformCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final long startTime = System.currentTimeMillis();
            final Path installationDir;

            if (self) {
                if (directory.isPresent()) {
                    installationDir = directory.get().toAbsolutePath();
                } else {
                    installationDir = detectProsperoInstallationPath().toAbsolutePath();
                }
                verifyInstallationContainsOnlyProspero(installationDir);
            } else {
                installationDir = determineInstallationDirectory(directory);
            }

            final MavenOptions mavenOptions = parseMavenOptions();
            final List<Repository> repositories = RepositoryDefinition.from(temporaryRepositories);

            log.tracef("Perform full update");

            final boolean changesApplied;
            try (UpdateAction updateAction = actionFactory.update(installationDir, mavenOptions, console, repositories)) {
                changesApplied = performUpdate(updateAction, yes, console, installationDir);
            }

            final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
            console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

            return changesApplied?ReturnCodes.SUCCESS_LOCAL_CHANGES:ReturnCodes.SUCCESS_NO_CHANGE;
        }

        private boolean performUpdate(UpdateAction updateAction, boolean yes, CliConsole console, Path installDir) throws OperationException, ProvisioningException {
            Path targetDir = null;
            try {
                targetDir = Files.createTempDirectory("update-candidate");
                if (buildUpdate(updateAction, targetDir, yes, console, () -> console.confirmUpdates())) {
                    ApplyCandidateAction applyCandidateAction = actionFactory.applyUpdate(installDir, targetDir);
                    final List<FileConflict> conflicts = applyCandidateAction.getConflicts();
                    if (!conflicts.isEmpty()) {
                        FileConflictPrinter.print(conflicts, console);
                        if (!yes && !console.confirm(CliMessages.MESSAGES.continueWithUpdate(), "", CliMessages.MESSAGES.updateCancelled())) {
                            return false;
                        }
                    }

                    applyCandidateAction.applyUpdate(ApplyCandidateAction.Type.UPDATE);
                } else {
                    return false;
                }
            } catch (IOException e) {
                throw Messages.MESSAGES.unableToCreateTemporaryDirectory(e);
            } finally {
                if (targetDir != null) {
                    FileUtils.deleteQuietly(targetDir.toFile());
                }
            }

            console.updatesComplete();
            return true;
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.PREPARE, sortOptions = false)
    public static class PrepareCommand extends AbstractMavenCommand {

        @CommandLine.Option(names = CliConstants.UPDATE_DIR, required = true)
        Path updateDirectory;

        @CommandLine.Option(names = {CliConstants.Y, CliConstants.YES})
        boolean yes;

        public PrepareCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final long startTime = System.currentTimeMillis();
            final Path installationDir = determineInstallationDirectory(directory);

            final MavenOptions mavenOptions = parseMavenOptions();
            final List<Repository> repositories = RepositoryDefinition.from(temporaryRepositories);

            log.tracef("Generate update in %s", updateDirectory);

            verifyTargetDirectoryIsEmpty(updateDirectory);

            final boolean updatesFound;
            try (UpdateAction updateAction = actionFactory.update(installationDir,
                    mavenOptions, console, repositories)) {
                updatesFound = buildUpdate(updateAction, updateDirectory, yes, console, ()->console.confirmBuildUpdates());
            }

            final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
            console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

            return updatesFound?ReturnCodes.SUCCESS_LOCAL_CHANGES:ReturnCodes.SUCCESS_NO_CHANGE;
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.APPLY, sortOptions = false)
    public static class ApplyCommand extends AbstractCommand {

        @CommandLine.Option(names = CliConstants.DIR)
        Optional<Path> directory;

        @CommandLine.Option(names = CliConstants.UPDATE_DIR, required = true)
        Path updateDir;

        @CommandLine.Option(names = {CliConstants.Y, CliConstants.YES})
        boolean yes;

        public ApplyCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final long startTime = System.currentTimeMillis();

            final Path installationDir = determineInstallationDirectory(directory);

            verifyDirectoryContainsInstallation(updateDir);

            if (!Files.exists(updateDir.resolve(MarkerFile.UPDATE_MARKER_FILE))) {
                throw CliMessages.MESSAGES.invalidUpdateCandidate(updateDir);
            }

            final ApplyCandidateAction applyCandidateAction = actionFactory.applyUpdate(installationDir.toAbsolutePath(), updateDir.toAbsolutePath());

            if (!applyCandidateAction.verifyCandidate(ApplyCandidateAction.Type.UPDATE)) {
                throw CliMessages.MESSAGES.updateCandidateStateNotMatched(installationDir, updateDir.toAbsolutePath());
            }

            console.updatesFound(applyCandidateAction.findUpdates().getArtifactUpdates());
            final List<FileConflict> conflicts = applyCandidateAction.getConflicts();
            FileConflictPrinter.print(conflicts, console);

            // there always should be updates, so confirm update
            if (!yes && !console.confirm(CliMessages.MESSAGES.continueWithUpdate(), "", CliMessages.MESSAGES.updateCancelled())) {
                return ReturnCodes.SUCCESS_NO_CHANGE;
            }

            applyCandidateAction.applyUpdate(ApplyCandidateAction.Type.UPDATE);

            final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
            console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

            return ReturnCodes.SUCCESS_LOCAL_CHANGES;
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.LIST, sortOptions = false)
    public static class ListCommand extends AbstractMavenCommand {

        public ListCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }
        @Override
        public Integer call() throws Exception {
            final Path installationDir = determineInstallationDirectory(directory);

            final MavenOptions mavenOptions = parseMavenOptions();
            final List<Repository> repositories = RepositoryDefinition.from(temporaryRepositories);

            try (UpdateAction updateAction = actionFactory.update(installationDir, mavenOptions, console, repositories)) {
                final UpdateSet updateSet = updateAction.findUpdates();
                console.updatesFound(updateSet.getArtifactUpdates());
                return ReturnCodes.SUCCESS_NO_CHANGE;
            }
        }
    }

    public UpdateCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory, CliConstants.Commands.UPDATE,
                List.of(
                    new UpdateCommand.PrepareCommand(console, actionFactory),
                    new UpdateCommand.ApplyCommand(console, actionFactory),
                    new UpdateCommand.PerformCommand(console, actionFactory),
                    new UpdateCommand.ListCommand(console, actionFactory))
        );
    }

    private static boolean buildUpdate(UpdateAction updateAction, Path updateDirectory, boolean yes, CliConsole console, Supplier<Boolean> confirmation) throws OperationException, ProvisioningException {
        final UpdateSet updateSet = updateAction.findUpdates();

        console.updatesFound(updateSet.getArtifactUpdates());
        if (updateSet.isEmpty()) {
            return false;
        }

        if (!yes && !confirmation.get()) {
            return false;
        }

        updateAction.buildUpdate(updateDirectory.toAbsolutePath());

        console.buildUpdatesComplete();

        return true;
    }

    public static void verifyInstallationContainsOnlyProspero(Path dir) throws ArgumentParsingException {
        verifyDirectoryContainsInstallation(dir);

        try {
            final List<String> fpNames = GalleonUtils.getInstalledPacks(dir.toAbsolutePath());
            if (fpNames.size() != 1) {
                throw CliMessages.MESSAGES.unexpectedPackageInSelfUpdate(dir.toString());
            }
            if (!fpNames.stream().allMatch(PROSPERO_FP_ZIP::equals)) {
                throw CliMessages.MESSAGES.unexpectedPackageInSelfUpdate(dir.toString());
            }
        } catch (ProvisioningException e) {
            throw CliMessages.MESSAGES.unableToParseSelfUpdateData(e);
        }
    }

    public static Path detectProsperoInstallationPath() throws ArgumentParsingException {
        final String modulePath = System.getProperty(JBOSS_MODULE_PATH);
        if (modulePath == null) {
            throw CliMessages.MESSAGES.unableToLocateProsperoInstallation();
        }
        return Paths.get(modulePath).toAbsolutePath().getParent();
    }

}
