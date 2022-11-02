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
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.RepositoryUtils;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.ProvisioningConfig;

public class ProvisioningAction {

    private final MavenSessionManager mavenSessionManager;
    private final Path installDir;
    private final Console console;

    public ProvisioningAction(Path installDir, MavenSessionManager mavenSessionManager, Console console) {
        this.installDir = installDir;
        this.console = console;
        this.mavenSessionManager = mavenSessionManager;
        verifyInstallDir(installDir);
    }

    /**
     * Installs feature pack defined by {@code fpl} in {@code installDir}. If {@code fpl} doesn't include version,
     * the newest available version will be used.
     *
     * @param config
     * @param channels
     * @throws ProvisioningException
     * @throws MetadataException
     */
    public void provision(ProvisioningConfig config, List<Channel> channels) throws ProvisioningException, OperationException {
        doProvision((pm,options)->pm.provision(config, options), channels);
    }

    private void doProvision(GalleonUtils.ProvisioningManagerExecution galleonOp, List<Channel> channels) throws ProvisioningException, OperationException {
        final ProsperoConfig prosperoConfig = new ProsperoConfig(channels);
        final GalleonEnvironment galleonEnv = GalleonEnvironment
                .builder(installDir, prosperoConfig, mavenSessionManager)
                .setConsole(console)
                .build();

        try {
            GalleonUtils.executeGalleon(options -> galleonOp.execute(galleonEnv.getProvisioningManager(), options),
                    mavenSessionManager.getProvisioningRepo().toAbsolutePath());
        } catch (UnresolvedMavenArtifactException e) {
            final List<RemoteRepository> repositories = galleonEnv.getChannels().stream()
                    .flatMap(c -> c.getRepositories().stream())
                    .map(r -> RepositoryUtils.toRemoteRepository(r.getId(), r.getUrl()))
                    .collect(Collectors.toList());
            throw new ArtifactResolutionException(e, repositories, mavenSessionManager.isOffline());
        }

        writeProsperoMetadata(installDir, galleonEnv.getRepositoryManager(), channels);
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, List<Channel> channels) throws MetadataException {
        final ChannelManifest manifest = maven.resolvedChannel();

        try (final InstallationMetadata installationMetadata = new InstallationMetadata(home, manifest, channels)) {
            installationMetadata.recordProvision(true);
        }
    }

    private static void verifyInstallDir(Path directory) {
        if (directory.toFile().isFile()) {
            // file exists and is a regular file
            throw Messages.MESSAGES.dirMustBeDirectory(directory);
        }
        if (!isEmptyDirectory(directory)) {
            throw Messages.MESSAGES.cannotInstallIntoNonEmptyDirectory(directory);
        }
    }

    private static boolean isEmptyDirectory(Path directory) {
        String[] list = directory.toFile().list();
        return list == null || list.length == 0;
    }
}