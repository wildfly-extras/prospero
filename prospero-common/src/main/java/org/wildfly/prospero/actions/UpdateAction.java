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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.util.PathsUtils;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Repository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.api.TemporaryRepositoriesHandler;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.updates.UpdateFinder;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

public class UpdateAction implements AutoCloseable {

    private final InstallationMetadata metadata;
    private final MavenSessionManager mavenSessionManager;
    private final Path installDir;
    private final Console console;
    private final List<Repository> overrideRepositories;
    private final ProsperoConfig prosperoConfig;

    public UpdateAction(Path installDir, MavenSessionManager mavenSessionManager, Console console, List<Repository> overrideRepositories)
            throws OperationException {
        this.metadata = new InstallationMetadata(installDir);
        this.installDir = installDir;
        this.console = console;
        this.overrideRepositories = overrideRepositories;
        this.prosperoConfig = addTemporaryRepositories(overrideRepositories);
        this.mavenSessionManager = mavenSessionManager;
    }

    public void performUpdate() throws OperationException, ProvisioningException {
        Path targetDir = null;
        try {
            targetDir = Files.createTempDirectory("update-eap");
            if (buildUpdate(targetDir)) {
                ApplyUpdateAction applyUpdateAction = new ApplyUpdateAction(installDir, targetDir);
                applyUpdateAction.applyUpdate();
            }
        } catch (IOException e) {
            throw new ProvisioningException("Unable to create temporary directory", e);
        } finally {
            if (targetDir != null) {
                FileUtils.deleteQuietly(targetDir.toFile());
            }
        }
    }

    public boolean buildUpdate(Path targetDir) throws ProvisioningException, OperationException {
        if (findUpdates().isEmpty()) {
            return false;
        }

        doBuildUpdate(targetDir);

        return true;
    }

    private void doBuildUpdate(Path targetDir) throws ProvisioningException, OperationException {
        final GalleonEnvironment galleonEnv = getGalleonEnv(targetDir);
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
            writeProsperoMetadata(targetDir, galleonEnv.getRepositoryManager(), metadata.getProsperoConfig().getChannels());
        } catch (MetadataException ex) {
            throw new ProvisioningException(ex);
        }
    }

    private GalleonEnvironment getGalleonEnv(Path target) throws ProvisioningException, OperationException {
        return GalleonEnvironment
                .builder(target, prosperoConfig.getChannels(), mavenSessionManager)
                .setSourceServerPath(this.installDir)
                .setConsole(console)
                .build();
    }

    public UpdateSet findUpdates() throws OperationException, ProvisioningException {
        final GalleonEnvironment galleonEnv = getGalleonEnv(installDir);
        try (final UpdateFinder updateFinder = new UpdateFinder(galleonEnv.getChannelSession(), galleonEnv.getProvisioningManager())) {
            return updateFinder.findUpdates(metadata.getArtifacts());
        }
    }

    @Override
    public void close() {
        metadata.close();
    }

    private ProsperoConfig addTemporaryRepositories(List<Repository> repositories) {
        final ProsperoConfig prosperoConfig = metadata.getProsperoConfig();

        final List<Channel> channels = TemporaryRepositoriesHandler.overrideRepositories(prosperoConfig.getChannels(), repositories);

        return new ProsperoConfig(channels);
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, List<Channel> channels) throws MetadataException {
        final ChannelManifest manifest = maven.resolvedChannel();

        try (final InstallationMetadata installationMetadata = new InstallationMetadata(home, manifest, channels)) {
            installationMetadata.recordProvision(true, false);
        }
    }
}
