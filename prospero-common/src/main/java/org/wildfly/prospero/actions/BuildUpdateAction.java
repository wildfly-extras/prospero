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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jboss.galleon.Constants;

import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.api.TemporaryRepositoriesHandler;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.updates.UpdateFinder;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.PathsUtils;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;

public class BuildUpdateAction implements AutoCloseable {

    private final InstallationMetadata installationMetadata;
    private final MavenSessionManager mavenSessionManager;
    private final GalleonEnvironment galleonEnv;
    private final ProsperoConfig prosperoConfig;
    private final Path installDir;
    private final Path targetDir;
    public BuildUpdateAction(Path installDir, Path targetDir, MavenSessionManager mavenSessionManager, Console console, List<Repository> overrideRepositories)
            throws ProvisioningException, OperationException {
        this.installDir = installDir;
        this.targetDir = targetDir;
        if (Files.exists(targetDir)) {
            IoUtils.recursiveDelete(targetDir);
        }
        this.installationMetadata = new InstallationMetadata(installDir);

        this.prosperoConfig = addTemporaryRepositories(overrideRepositories);

        galleonEnv = GalleonEnvironment
                .builder(targetDir, prosperoConfig.getChannels(), mavenSessionManager)
                .setConsole(console)
                .build();
        this.mavenSessionManager = mavenSessionManager;
    }

    public void buildUpdate() throws ProvisioningException, MetadataException, ArtifactResolutionException {
        if (findUpdates().isEmpty()) {
            return;
        }

        buildUpdates();

    }

    public UpdateSet findUpdates() throws ArtifactResolutionException, ProvisioningException {
        try (final UpdateFinder updateFinder = new UpdateFinder(galleonEnv.getChannelSession(), galleonEnv.getProvisioningManager())) {
            return updateFinder.findUpdates(installationMetadata.getArtifacts());
        }
    }

    @Override
    public void close() {

    }

    private ProsperoConfig addTemporaryRepositories(List<Repository> repositories) {
        final ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();

        final List<Channel> channels = TemporaryRepositoriesHandler.overrideRepositories(prosperoConfig.getChannels(), repositories);

        return new ProsperoConfig(channels);
    }

    private void buildUpdates() throws ProvisioningException, ArtifactResolutionException {
        final ProvisioningManager provMgr = galleonEnv.getProvisioningManager();
        try {
            GalleonUtils.executeGalleon((options) -> {
                options.put(Constants.EXPORT_SYSTEM_PATHS, "true");
                provMgr.provision(ProvisioningXmlParser.parse(PathsUtils.getProvisioningXml(installDir)), options);
            },
                    mavenSessionManager.getProvisioningRepo().toAbsolutePath());
        } catch (UnresolvedMavenArtifactException e) {
            throw new ArtifactResolutionException(e, prosperoConfig.listAllRepositories(), mavenSessionManager.isOffline());
        }
        try {
            writeProsperoMetadata(targetDir, galleonEnv.getRepositoryManager(), installationMetadata.getProsperoConfig().getChannels());
        } catch (MetadataException ex) {
            throw new ProvisioningException(ex);
        }
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, List<Channel> channels) throws MetadataException {
        final ChannelManifest manifest = maven.resolvedChannel();

        try (final InstallationMetadata installationMetadata = new InstallationMetadata(home, manifest, channels)) {
            installationMetadata.recordProvision(true, false);
        }
    }
}
