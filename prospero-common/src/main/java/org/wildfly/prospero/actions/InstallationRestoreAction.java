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
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.TemporaryRepositoriesHandler;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class InstallationRestoreAction {

    private final Path installDir;
    private final Console console;
    private final MavenSessionManager mavenSessionManager;

    public InstallationRestoreAction(Path installDir, MavenSessionManager mavenSessionManager, Console console) {
        this.installDir = installDir;
        this.mavenSessionManager = mavenSessionManager;
        this.console = console;
    }

    public void restore(Path metadataBundleZip, List<Repository> remoteRepositories)
            throws ProvisioningException, IOException, OperationException {
        if (installDir.toFile().exists()) {
            throw Messages.MESSAGES.installationDirAlreadyExists(installDir);
        }

        try (final InstallationMetadata metadataBundle = InstallationMetadata.importMetadata(metadataBundleZip)) {
            final ProsperoConfig prosperoConfig = metadataBundle.getProsperoConfig();
            List<Channel> originalChannels = new ArrayList<>(prosperoConfig.getChannels());
            if (remoteRepositories != null && !remoteRepositories.isEmpty()) {
                prosperoConfig.getChannels().clear();
                prosperoConfig.getChannels().addAll(TemporaryRepositoriesHandler.overrideRepositories(originalChannels, remoteRepositories));
            }
            final GalleonEnvironment galleonEnv = GalleonEnvironment
                    .builder(installDir, prosperoConfig.getChannels(), mavenSessionManager)
                    .setConsole(console)
                    .setRestoreManifest(metadataBundle.getManifest())
                    .build();

            try {
                GalleonUtils.executeGalleon(options -> galleonEnv.getProvisioningManager().provision(metadataBundle.getGalleonProvisioningConfig(), options),
                        mavenSessionManager.getProvisioningRepo().toAbsolutePath());
            } catch (UnresolvedMavenArtifactException e) {
                throw new ArtifactResolutionException(e, prosperoConfig.listAllRepositories(), mavenSessionManager.isOffline());
            }

            writeProsperoMetadata(galleonEnv.getRepositoryManager(), originalChannels);
        }
    }

    private void writeProsperoMetadata(ChannelMavenArtifactRepositoryManager maven, List<Channel> channels) throws MetadataException {
        final ChannelManifest manifest = maven.resolvedChannel();

        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installDir, manifest, channels)) {
            installationMetadata.recordProvision(true);
        }
    }
}
