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

import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Repository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.TemporaryRepositoriesHandler;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InstallationRestoreAction {

    private final Path installDir;
    private final Console console;
    private final MavenSessionManager mavenSessionManager;

    public InstallationRestoreAction(Path installDir, MavenOptions mavenOptions, Console console) throws ProvisioningException {
        this.installDir = installDir;
        this.mavenSessionManager = new MavenSessionManager(mavenOptions);
        this.console = console;
    }

    public void restore(Path metadataBundleZip, List<Repository> remoteRepositories)
            throws ProvisioningException, IOException, OperationException {
        if (installDir.toFile().exists()) {
            throw ProsperoLogger.ROOT_LOGGER.installationDirAlreadyExists(installDir);
        }

        try (InstallationMetadata metadataBundle = InstallationMetadata.fromMetadataBundle(metadataBundleZip)) {
            final ProsperoConfig prosperoConfig = metadataBundle.getProsperoConfig();
            List<Channel> originalChannels = new ArrayList<>(prosperoConfig.getChannels());
            if (remoteRepositories != null && !remoteRepositories.isEmpty()) {
                prosperoConfig.getChannels().clear();
                prosperoConfig.getChannels().addAll(TemporaryRepositoriesHandler.overrideRepositories(originalChannels, remoteRepositories));
            }
            try (GalleonEnvironment galleonEnv = GalleonEnvironment
                    .builder(installDir, prosperoConfig.getChannels(), mavenSessionManager, false)
                    .setConsole(console)
                    .setRestoreManifest(metadataBundle.getManifest())
                    .build()) {

                GalleonUtils.executeGalleon(options -> galleonEnv.getProvisioning().provision(metadataBundle.getGalleonProvisioningConfig(), options),
                        mavenSessionManager.getProvisioningRepo().toAbsolutePath());

                writeProsperoMetadata(galleonEnv.getChannelSession().getRecordedChannel(), originalChannels);
            } catch (UnresolvedMavenArtifactException e) {
                throw new ArtifactResolutionException(ProsperoLogger.ROOT_LOGGER.unableToResolve(), e, e.getUnresolvedArtifacts(),
                        e.getAttemptedRepositories(), mavenSessionManager.isOffline());
            }
        }
    }

    private void writeProsperoMetadata(ChannelManifest manifest, List<Channel> channels) throws MetadataException {
        try (InstallationMetadata installationMetadata = InstallationMetadata.newInstallation(installDir, manifest,
                new ProsperoConfig(channels), Optional.empty())) {
            installationMetadata.recordProvision(true, true);
        }
    }
}
