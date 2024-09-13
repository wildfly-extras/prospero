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
import org.jboss.galleon.util.PathsUtils;
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
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.galleon.api.config.GalleonProvisioningConfig;

import static org.wildfly.prospero.galleon.GalleonUtils.MAVEN_REPO_LOCAL;

public class InstallationHistoryAction {

    private final Path installation;
    private final Console console;

    public InstallationHistoryAction(Path installation, Console console) {
        this.installation = InstallFolderUtils.toRealPath(installation);
        this.console = console;
    }

    /**
     * gets a list of changes related to the {@code savedState}.
     *
     * @param savedState
     * @return
     * @throws MetadataException
     */
    public InstallationChanges getRevisionChanges(SavedState savedState) throws MetadataException {
        ProsperoLogger.ROOT_LOGGER.historyDetails(savedState.getName(), installation);
        final InstallationMetadata installationMetadata = InstallationMetadata.loadInstallation(installation);
        verifyStateExists(savedState, installationMetadata);
        return installationMetadata.getChangesIn(savedState);
    }

    public InstallationChanges getChangesSinceRevision(SavedState savedState) throws MetadataException {
        ProsperoLogger.ROOT_LOGGER.historyDetails(savedState.getName(), installation);
        final InstallationMetadata installationMetadata = InstallationMetadata.loadInstallation(installation);
        verifyStateExists(savedState, installationMetadata);
        return installationMetadata.getChangesToCurrent(savedState);
    }

    public List<SavedState> getRevisions() throws MetadataException {
        ProsperoLogger.ROOT_LOGGER.listHistory(installation);
        try(InstallationMetadata installationMetadata = InstallationMetadata.loadInstallation(installation)) {
            return installationMetadata.getRevisions();
        }
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
        if (Files.exists(targetDir)) {
            InstallFolderUtils.verifyIsEmptyDir(targetDir);
        } else {
            InstallFolderUtils.verifyIsWritable(targetDir);
        }

        targetDir = InstallFolderUtils.toRealPath(targetDir);

        try (InstallationMetadata metadata = InstallationMetadata.loadInstallation(installation)) {
            ProsperoLogger.ROOT_LOGGER.revertCandidateStarted(installation);

            verifyStateExists(savedState, metadata);
            verifyStateIsNotTip(savedState, metadata);

            MavenSessionManager mavenSessionManager = new MavenSessionManager(mavenOptions);
            try (InstallationMetadata revertMetadata = metadata.getSavedState(savedState)) {
                final ProsperoConfig prosperoConfig = new ProsperoConfig(
                        TemporaryRepositoriesHandler.overrideRepositories(revertMetadata.getProsperoConfig().getChannels(), overrideRepositories));
                try (GalleonEnvironment galleonEnv = GalleonEnvironment
                        .builder(targetDir, prosperoConfig.getChannels(), mavenSessionManager, false)
                        .setConsole(console)
                        .setRestoreManifest(revertMetadata.getManifest(), revertMetadata.getManifestVersions().orElse(null))
                        .setSourceServerPath(installation)
                        .build();
                     PrepareCandidateAction prepareCandidateAction = new PrepareCandidateAction(installation,
                             mavenSessionManager, revertMetadata.getProsperoConfig(), console)) {

                    System.setProperty(MAVEN_REPO_LOCAL, mavenSessionManager.getProvisioningRepo().toAbsolutePath().toString());

                    GalleonProvisioningConfig provisioningConfig = revertMetadata.getRecordedProvisioningConfig();
                    if (provisioningConfig == null) {
                        ProsperoLogger.ROOT_LOGGER.fallbackToGalleonProvisioningDefinition();
                        provisioningConfig = galleonEnv.getProvisioning().loadProvisioningConfig(PathsUtils.getProvisioningXml(installation));
                    }

                    prepareCandidateAction.buildCandidate(targetDir, galleonEnv,
                            ApplyCandidateAction.Type.REVERT, provisioningConfig,
                            UpdateSet.EMPTY, (channels) -> revertMetadata.getManifestVersions());
                }

                ProsperoLogger.ROOT_LOGGER.revertCandidateCompleted(targetDir);
            } finally {
                System.clearProperty(MAVEN_REPO_LOCAL);
            }
        }
    }

    private static void verifyStateExists(SavedState savedState, InstallationMetadata metadata) throws MetadataException {
        if (metadata.getRevisions().stream().noneMatch(s->s.getName().equals(savedState.getName()))) {
            throw ProsperoLogger.ROOT_LOGGER.savedStateNotFound(savedState.getName());
        }
    }

    private static void verifyStateIsNotTip(SavedState savedState, InstallationMetadata metadata) throws MetadataException {
        Optional<SavedState> first = metadata.getRevisions().stream().findFirst();
        if (first.isPresent() && first.get().getName().equals(savedState.getName())) {
            throw ProsperoLogger.ROOT_LOGGER.cannotRevertToTip(savedState.getName());
        }
    }
}
