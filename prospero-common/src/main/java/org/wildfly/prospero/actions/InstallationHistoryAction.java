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

package org.wildfly.prospero.actions;

import org.apache.commons.io.FileUtils;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationChanges;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.TemporaryRepositoriesHandler;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.wildfly.prospero.actions.ApplyCandidateAction.Type.ROLLBACK;
import static org.wildfly.prospero.galleon.GalleonUtils.MAVEN_REPO_LOCAL;

public class InstallationHistoryAction {

    private final Path installation;
    private final Console console;

    public InstallationHistoryAction(Path installation, Console console) {
        this.installation = installation;
        this.console = console;
    }

    public InstallationChanges compare(SavedState savedState) throws MetadataException {
        final InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        verifyStateExists(savedState, installationMetadata);
        return installationMetadata.getChangesSince(savedState);
    }

    public List<SavedState> getRevisions() throws MetadataException {
        final InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        return installationMetadata.getRevisions();
    }

    public void rollback(SavedState savedState, MavenOptions mavenOptions, List<Repository> overrideRepositories) throws OperationException, ProvisioningException {
        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("eap-revert");
            prepareRevert(savedState, mavenOptions, overrideRepositories, tempDirectory);
            new ApplyCandidateAction(installation, tempDirectory).applyUpdate(ApplyCandidateAction.Type.ROLLBACK);
        } catch (IOException e) {
            throw Messages.MESSAGES.unableToCreateTemporaryDirectory(e);
        } finally {
            if (tempDirectory != null) {
                FileUtils.deleteQuietly(tempDirectory.toFile());
            }
        }
    }

    public void prepareRevert(SavedState savedState, MavenOptions mavenOptions, List<Repository> overrideRepositories, Path targetDir)
            throws OperationException, ProvisioningException {

        try (final InstallationMetadata metadata = new InstallationMetadata(installation)) {

            verifyStateExists(savedState, metadata);

            final ProsperoConfig prosperoConfig = new ProsperoConfig(
                    TemporaryRepositoriesHandler.overrideRepositories(metadata.getProsperoConfig().getChannels(), overrideRepositories));

            MavenSessionManager mavenSessionManager = new MavenSessionManager(mavenOptions);
            try (final InstallationMetadata revertMetadata = metadata.getSavedState(savedState)) {
                final GalleonEnvironment galleonEnv = GalleonEnvironment
                        .builder(targetDir, prosperoConfig.getChannels(), mavenSessionManager)
                        .setConsole(console)
                        .setRestoreManifest(revertMetadata.getManifest())
                        .build();

                System.setProperty(MAVEN_REPO_LOCAL, mavenSessionManager.getProvisioningRepo().toAbsolutePath().toString());
                try(final PrepareCandidateAction prepareCandidateAction = new PrepareCandidateAction(installation, mavenSessionManager, console, prosperoConfig)) {
                    prepareCandidateAction.buildCandidate(targetDir, galleonEnv, ROLLBACK);
                }
            } finally {
                System.clearProperty(MAVEN_REPO_LOCAL);
            }
        }
    }

    public void applyRevert(Path updateDirectory) throws OperationException, ProvisioningException {
        final ApplyCandidateAction applyAction = new ApplyCandidateAction(installation, updateDirectory);
        if (!applyAction.verifyCandidate(ApplyCandidateAction.Type.ROLLBACK)) {
            throw Messages.MESSAGES.invalidRollbackCandidate(updateDirectory, installation);
        }

        applyAction.applyUpdate(ApplyCandidateAction.Type.ROLLBACK);
    }

    private static void verifyStateExists(SavedState savedState, InstallationMetadata metadata) throws MetadataException {
        if (!metadata.getRevisions().stream().filter(s->s.getName().equals(savedState.getName())).findFirst().isPresent()) {
            throw Messages.MESSAGES.savedStateNotFound(savedState.getName());
        }
    }
}
