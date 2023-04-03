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
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.InstallationChanges;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.TemporaryRepositoriesHandler;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.wildfly.prospero.actions.ApplyCandidateAction.Type.REVERT;
import static org.wildfly.prospero.galleon.GalleonUtils.MAVEN_REPO_LOCAL;
import static org.wildfly.prospero.metadata.ProsperoMetadataUtils.CURRENT_VERSION_FILE;
import static org.wildfly.prospero.metadata.ProsperoMetadataUtils.METADATA_DIR;

public class InstallationHistoryAction {

    private final Path installation;
    private final Console console;

    public InstallationHistoryAction(Path installation, Console console) {
        this.installation = installation;
        this.console = console;
    }

    public InstallationChanges compare(SavedState savedState) throws MetadataException {
        ProsperoLogger.ROOT_LOGGER.historyDetails(savedState.getName(), installation);
        final InstallationMetadata installationMetadata = InstallationMetadata.loadInstallation(installation);
        verifyStateExists(savedState, installationMetadata);
        return installationMetadata.getChangesSince(savedState);
    }

    public List<SavedState> getRevisions() throws MetadataException {
        ProsperoLogger.ROOT_LOGGER.listHistory(installation);
        final InstallationMetadata installationMetadata = InstallationMetadata.loadInstallation(installation);
        return installationMetadata.getRevisions();
    }

    public void rollback(SavedState savedState, MavenOptions mavenOptions, List<Repository> overrideRepositories) throws OperationException, ProvisioningException {
        Path tempDirectory = null;
        try {
            ProsperoLogger.ROOT_LOGGER.revertStarted(installation, savedState.getName());
            tempDirectory = Files.createTempDirectory("revert-candidate");
            if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                ProsperoLogger.ROOT_LOGGER.temporaryCandidateFolder(tempDirectory);
            }
            prepareRevert(savedState, mavenOptions, overrideRepositories, tempDirectory);
            new ApplyCandidateAction(installation, tempDirectory).applyUpdate(ApplyCandidateAction.Type.REVERT);
            ProsperoLogger.ROOT_LOGGER.revertCompleted(installation, savedState.getName());
        } catch (IOException e) {
            throw ProsperoLogger.ROOT_LOGGER.unableToCreateTemporaryDirectory(e);
        } finally {
            if (tempDirectory != null) {
                FileUtils.deleteQuietly(tempDirectory.toFile());
            }
        }
    }

    public void prepareRevert(SavedState savedState, MavenOptions mavenOptions, List<Repository> overrideRepositories, Path targetDir)
            throws OperationException, ProvisioningException {
        try (final InstallationMetadata metadata = InstallationMetadata.loadInstallation(installation)) {
            ProsperoLogger.ROOT_LOGGER.revertCandidateStarted(installation);

            verifyStateExists(savedState, metadata);

            MavenSessionManager mavenSessionManager = new MavenSessionManager(mavenOptions);
            try (final InstallationMetadata revertMetadata = metadata.getSavedState(savedState)) {
                final ProsperoConfig prosperoConfig = new ProsperoConfig(
                        TemporaryRepositoriesHandler.overrideRepositories(revertMetadata.getProsperoConfig().getChannels(), overrideRepositories));
                final GalleonEnvironment galleonEnv = GalleonEnvironment
                        .builder(targetDir, prosperoConfig.getChannels(), mavenSessionManager)
                        .setConsole(console)
                        .setRestoreManifest(revertMetadata.getManifest())
                        .build();

                System.setProperty(MAVEN_REPO_LOCAL, mavenSessionManager.getProvisioningRepo().toAbsolutePath().toString());
                try(final PrepareCandidateAction prepareCandidateAction = new PrepareCandidateAction(installation,
                        mavenSessionManager, console, revertMetadata.getProsperoConfig())) {
                    prepareCandidateAction.buildCandidate(targetDir, galleonEnv, REVERT);
                }

                revertCurrentVersions(targetDir, revertMetadata);

                ProsperoLogger.ROOT_LOGGER.revertCandidateCompleted(targetDir);
            } finally {
                System.clearProperty(MAVEN_REPO_LOCAL);
            }
        }
    }

    private static void revertCurrentVersions(Path targetDir, InstallationMetadata revertMetadata) throws MetadataException {
        try {
            final Optional<ManifestVersionRecord> manifestHistory = revertMetadata.getManifestVersions();
            if (manifestHistory.isPresent()) {
                ProsperoMetadataUtils.writeVersionRecord(targetDir.resolve(METADATA_DIR).resolve(CURRENT_VERSION_FILE), manifestHistory.get());
            } else {
                Path versionsFile = targetDir.resolve(METADATA_DIR).resolve(CURRENT_VERSION_FILE);
                if (Files.exists(versionsFile)) {
                    Files.delete(versionsFile);
                }
            }
        } catch (IOException e) {
            throw ProsperoLogger.ROOT_LOGGER.unableToWriteFile(targetDir.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(ProsperoMetadataUtils.CURRENT_VERSION_FILE), e);
        }
    }

    private static void verifyStateExists(SavedState savedState, InstallationMetadata metadata) throws MetadataException {
        if (!metadata.getRevisions().stream().filter(s->s.getName().equals(savedState.getName())).findFirst().isPresent()) {
            throw ProsperoLogger.ROOT_LOGGER.savedStateNotFound(savedState.getName());
        }
    }
}
