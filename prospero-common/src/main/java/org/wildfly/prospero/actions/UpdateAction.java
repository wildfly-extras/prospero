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

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.jboss.galleon.util.PathsUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.ChannelVersion;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.FileConflict;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.TemporaryRepositoriesHandler;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.updates.ChannelUpdateFinder;
import org.wildfly.prospero.updates.ChannelsUpdateResult;
import org.wildfly.prospero.updates.UpdateFinder;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;

public class UpdateAction implements AutoCloseable {

    private final InstallationMetadata metadata;
    private final MavenSessionManager mavenSessionManager;
    private final Path installDir;
    private final Console console;
    private final ProsperoConfig prosperoConfig;
    private final MavenOptions mavenOptions;

    public UpdateAction(Path installDir, MavenOptions mavenOptions, Console console, List<Repository> overrideRepositories)
            throws OperationException, ProvisioningException {
        this(installDir, addTemporaryRepositories(installDir, overrideRepositories), mavenOptions, console);
    }

    public UpdateAction(Path installDir, List<Channel> overrideChannels, MavenOptions mavenOptions, Console console)
            throws OperationException, ProvisioningException {
        this.installDir = InstallFolderUtils.toRealPath(installDir);
        this.metadata = InstallationMetadata.loadInstallation(this.installDir);
        this.console = console;
        final ProsperoConfig prosperoConfig = metadata.getProsperoConfig();
        if (overrideChannels.isEmpty()) {
            this.prosperoConfig = prosperoConfig;
        } else {
            this.prosperoConfig = new ProsperoConfig(overrideChannels, prosperoConfig.getMavenOptions());
        }
        this.mavenOptions = prosperoConfig.getMavenOptions().merge(mavenOptions);

        this.mavenSessionManager = new MavenSessionManager(this.mavenOptions);
    }

    /**
     * performs a full in-place update of {@code installDir}.
     *
     * @return list of conflicts if any found during update.
     *
     * @throws OperationException
     * @throws ProvisioningException
     */
    public List<FileConflict> performUpdate() throws OperationException, ProvisioningException {
        ProsperoLogger.ROOT_LOGGER.performUpdateStarted(installDir);
        Path targetDir = null;
        try {
            targetDir = Files.createTempDirectory("update-candidate");
            if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                ProsperoLogger.ROOT_LOGGER.temporaryCandidateFolder(targetDir);
            }
            if (buildUpdate(targetDir)) {
                final ApplyCandidateAction applyCandidateAction = new ApplyCandidateAction(installDir, targetDir);
                return applyCandidateAction.applyUpdate(ApplyCandidateAction.Type.UPDATE);
            } else {
                return Collections.emptyList();
            }
        } catch (IOException e) {
            throw ProsperoLogger.ROOT_LOGGER.unableToCreateTemporaryDirectory(e);
        } finally {
            if (targetDir != null) {
                FileUtils.deleteQuietly(targetDir.toFile());
            }
        }
    }

    /**
     * builds an update candidate for {@code installDir}. The candidate is placed in {@code targetDir}.
     * The candidate is only built if there are
     *
     * @param targetDir path where the update candidate should be placed.
     * @return true if the candidate was created, false if no updates were found.
     * @throws ProvisioningException
     * @throws OperationException
     */
    public boolean buildUpdate(Path targetDir) throws ProvisioningException, OperationException {
        if (Files.exists(targetDir)) {
            InstallFolderUtils.verifyIsEmptyDir(targetDir);
        } else {
            InstallFolderUtils.verifyIsWritable(targetDir);
        }

        targetDir = InstallFolderUtils.toRealPath(targetDir);

        final UpdateSet updateSet = findUpdates();
        if (updateSet.isEmpty()) {
            ProsperoLogger.ROOT_LOGGER.noUpdatesFound(installDir);
            return false;
        }

        ProsperoLogger.ROOT_LOGGER.updateCandidateStarted(installDir);
        try (PrepareCandidateAction prepareCandidateAction = new PrepareCandidateAction(installDir, mavenSessionManager, prosperoConfig);
             GalleonEnvironment galleonEnv = getGalleonEnv(targetDir)) {
            try (Provisioning p = new GalleonBuilder().newProvisioningBuilder(PathsUtils.getProvisioningXml(installDir)).build()) {
                final GalleonProvisioningConfig provisioningConfig = p.loadProvisioningConfig(PathsUtils.getProvisioningXml(installDir));

                final boolean result = prepareCandidateAction.buildCandidate(targetDir, galleonEnv,
                        ApplyCandidateAction.Type.UPDATE, provisioningConfig, updateSet);
                ProsperoLogger.ROOT_LOGGER.updateCandidateCompleted(targetDir);
                return result;
            }
        }
    }

    /**
     * generate a list of updates that can be applied to server at {@code installDir}.
     *
     * @return
     * @throws OperationException
     * @throws ProvisioningException
     */
    public UpdateSet findUpdates() throws OperationException, ProvisioningException {
        ProsperoLogger.ROOT_LOGGER.checkingUpdates();
        try (GalleonEnvironment galleonEnv = getGalleonEnv(installDir);
             UpdateFinder updateFinder = new UpdateFinder(galleonEnv.getChannelSession())) {

            final UpdateSet updates = updateFinder.findUpdates(metadata.getArtifacts(), metadata.getChannelVersions());
            ProsperoLogger.ROOT_LOGGER.updatesFound(updates.getArtifactUpdates().size());
            return updates;
        }
    }

    private GalleonEnvironment getGalleonEnv(Path target) throws ProvisioningException, OperationException {
        return GalleonEnvironment
                .builder(target, prosperoConfig.getChannels(), mavenSessionManager, false)
                .setSourceServerPath(this.installDir)
                .setConsole(console)
                .build();
    }

    @Override
    public void close() {
        metadata.close();
    }

    private static List<Channel> addTemporaryRepositories(Path installDir, List<Repository> repositories) throws MetadataException {
        try(InstallationMetadata metadata = InstallationMetadata.loadInstallation(installDir)) {
            final ProsperoConfig prosperoConfig = metadata.getProsperoConfig();

            return TemporaryRepositoriesHandler.overrideRepositories(prosperoConfig.getChannels(), repositories);
        }
    }

    public ChannelsUpdateResult findChannelUpdates() throws MetadataException {
        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system);
        final ChannelUpdateFinder finder = new ChannelUpdateFinder(system, session);
        ChannelsUpdateResult versions = new ChannelsUpdateResult();
        for (Channel channel : prosperoConfig.getChannels()) {
            final List<ChannelVersion> channelVersions = metadata.getChannelVersions();
            final Optional<ChannelVersion> cv = channelVersions.stream().filter(c -> c.getChannelName().equals(channel.getName())).findFirst();

            if (cv.isPresent()) {
                final ChannelVersion channelVersion = cv.get();
                if (channelVersion.getType() == ChannelVersion.Type.MAVEN) {
                    try {
                        versions.addChannelVersions(channel.getName(), channelVersion.getPhysicalVersion(), finder.findNewerVersions(channel, channelVersion));
                    } catch (VersionRangeResolutionException e) {
                        throw new RuntimeException(e);
                    } catch (ArtifactResolutionException e) {
                        throw new RuntimeException(e);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    versions.addUnsupportedChannel(channel.getName());
                }
            }
        }
        return versions;
    }
}
