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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.actions.BuildUpdateAction;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.ReturnCodes;
import static org.wildfly.prospero.cli.commands.UpdateCommand.detectProsperoInstallationPath;
import static org.wildfly.prospero.cli.commands.UpdateCommand.verifyInstallationContainsOnlyProspero;
import org.wildfly.prospero.cli.commands.options.LocalRepoOptions;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.Commands.BUILD_UPDATE,
        sortOptions = false
)
public class BuildUpdateCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.SELF)
    boolean self;

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    @CommandLine.Option(names = CliConstants.TARGET_DIR, required = true)
    Optional<Path> targetDir;

    @CommandLine.Option(names = CliConstants.DRY_RUN)
    boolean dryRun;

    @CommandLine.Option(names = CliConstants.OFFLINE)
    boolean offline;

    @CommandLine.Option(names = {CliConstants.Y, CliConstants.YES})
    boolean yes;

    @CommandLine.ArgGroup(exclusive = true, headingKey = "localRepoOptions.heading")
    LocalRepoOptions localRepoOptions;

    @CommandLine.Option(
            names = CliConstants.REPOSITORIES,
            paramLabel = CliConstants.REPO_URL,
            descriptionKey = "update.remote-repositories",
            split = ",",
            order = 5
    )
    List<String> temporaryRepositories = new ArrayList<>();

    public BuildUpdateCommand(Console console, ActionFactory actionFactory) {
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

        final MavenSessionManager mavenSessionManager = new MavenSessionManager(LocalRepoOptions.getLocalMavenCache(localRepoOptions), offline);

        final List<Repository> repositories = RepositoryDefinition.from(temporaryRepositories);

        try (BuildUpdateAction buildUpdateAction = actionFactory.buildUpdate(installationDir, targetDir.get().toAbsolutePath(), mavenSessionManager, console, repositories)) {
            if (!dryRun) {
                buildUpdate(buildUpdateAction);
            } else {
                final UpdateSet updateSet = buildUpdateAction.findUpdates();

                console.updatesFound(updateSet.getArtifactUpdates());
            }
        }

        final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
        console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

        return ReturnCodes.SUCCESS;
    }

    private void buildUpdate(BuildUpdateAction buildUpdateAction) throws ArtifactResolutionException, ProvisioningException, MetadataException {
        final UpdateSet updateSet = buildUpdateAction.findUpdates();

        console.updatesFound(updateSet.getArtifactUpdates());
        if (updateSet.isEmpty()) {
            return;
        }

        if (!yes && !console.confirmBuildUpdates()) {
            return;
        }

        buildUpdateAction.buildUpdate();

        console.buildUpdatesComplete();
    }
}
