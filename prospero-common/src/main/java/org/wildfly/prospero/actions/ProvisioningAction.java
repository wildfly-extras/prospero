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

import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.FeaturePackLocationParser;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.wildfly.channel.Channel;

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
     * @param provisioningDefinition
     * @throws ProvisioningException
     * @throws MetadataException
     */
    public void provision(ProvisioningDefinition provisioningDefinition) throws ProvisioningException, OperationException {
        final List<RemoteRepository> repositories = provisioningDefinition.getRepositories();
        final GalleonEnvironment galleonEnv = GalleonEnvironment
                .builder(installDir, provisioningDefinition.getProsperoConfig(), mavenSessionManager)
                .setConsole(console)
                .build();

        final ChannelMavenArtifactRepositoryManager repositoryManager = galleonEnv.getRepositoryManager();
        final ProvisioningConfig config;
        if (provisioningDefinition.getFpl() != null) {
            FeaturePackLocation loc = new FeaturePackLocationParser(repositoryManager).resolveFpl(provisioningDefinition.getFpl());

            console.println(Messages.MESSAGES.installingFpl(loc.toString()));

            final FeaturePackConfig.Builder configBuilder = FeaturePackConfig.builder(loc);
            for (String includedPackage : provisioningDefinition.getIncludedPackages()) {
                configBuilder.includePackage(includedPackage);
            }
            config = ProvisioningConfig.builder().addFeaturePackDep(configBuilder.build()).build();
        } else {
            config = ProvisioningXmlParser.parse(provisioningDefinition.getDefinition());
        }

        try {
            GalleonUtils.executeGalleon(options -> galleonEnv.getProvisioningManager().provision(config, options),
                    mavenSessionManager.getProvisioningRepo().toAbsolutePath());
        } catch (UnresolvedMavenArtifactException e) {
            throw new ArtifactResolutionException(e, repositories, mavenSessionManager.isOffline());
        }

        writeProsperoMetadata(installDir, repositoryManager, galleonEnv.getChannelRefs(), repositories);
    }

    /**
     * Installs feature pack based on Galleon installation file
     *
     * @param installationFile
     * @param channelRefs
     * @param repositories
     * @throws ProvisioningException
     * @throws IOException
     * @throws MetadataException
     */
    public void provision(Path installationFile, List<ChannelRef> channelRefs, List<RemoteRepository> repositories) throws ProvisioningException, OperationException {
        if (Files.exists(installDir)) {
            throw Messages.MESSAGES.installationDirAlreadyExists(installDir);
        }
        final ProsperoConfig prosperoConfig = new ProsperoConfig(channelRefs, repositories.stream().map(RepositoryRef::new).collect(Collectors.toList()));
        final GalleonEnvironment galleonEnv = GalleonEnvironment
                .builder(installDir, prosperoConfig, mavenSessionManager)
                .setConsole(console)
                .build();

        try {
            GalleonUtils.executeGalleon(options->galleonEnv.getProvisioningManager().provision(installationFile, options),
                    mavenSessionManager.getProvisioningRepo().toAbsolutePath());
        } catch (UnresolvedMavenArtifactException e) {
            throw new ArtifactResolutionException(e, repositories, mavenSessionManager.isOffline());
        }

        writeProsperoMetadata(installDir, galleonEnv.getRepositoryManager(), channelRefs, repositories);
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, List<ChannelRef> channelRefs,
                                       List<RemoteRepository> repositories) throws MetadataException {
        final Channel channel = maven.resolvedChannel();

        try (final InstallationMetadata installationMetadata = new InstallationMetadata(home, channel, channelRefs, repositories)) {
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
