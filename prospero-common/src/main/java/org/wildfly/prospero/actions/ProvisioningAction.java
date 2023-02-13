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

import org.apache.commons.lang3.StringUtils;
import org.jboss.galleon.config.ProvisioningConfig;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.RepositoryUtils;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonFeaturePackAnalyzer;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.wildfly.prospero.licenses.License;
import org.wildfly.prospero.licenses.LicenseManager;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;

public class ProvisioningAction {

    private static final String CHANNEL_NAME_PREFIX = "channel-";
    private final MavenSessionManager mavenSessionManager;
    private final Path installDir;
    private final Console console;
    private final LicenseManager licenseManager;
    private final MavenOptions mvnOptions;

    public ProvisioningAction(Path installDir, MavenOptions mvnOptions, Console console) throws ProvisioningException {
        this.installDir = installDir;
        this.console = console;
        this.mvnOptions = mvnOptions;
        this.mavenSessionManager = new MavenSessionManager(mvnOptions);
        this.licenseManager = new LicenseManager();

        verifyInstallDir(installDir);
    }

    /**
     * Provision installation according to given ProvisioningDefinition.
     *
     * <b>NOTE:</b> All required licenses are assumed to be accepted by calling this method.
     *
     * @param provisioningConfig prospero provisioning definition
     * @param channels list of channels to resolve installed artifacts
     */
    public void provision(ProvisioningConfig provisioningConfig, List<Channel> channels)
            throws ProvisioningException, OperationException, MalformedURLException {
        channels = enforceChannelNames(channels);

        final GalleonEnvironment galleonEnv = GalleonEnvironment
                .builder(installDir, channels, mavenSessionManager)
                .setConsole(console)
                .build();

        try {
            GalleonUtils.executeGalleon(options -> galleonEnv.getProvisioningManager().provision(provisioningConfig, options),
                    mavenSessionManager.getProvisioningRepo().toAbsolutePath());
        } catch (UnresolvedMavenArtifactException e) {
            final List<RemoteRepository> repositories = galleonEnv.getChannels().stream()
                    .flatMap(c -> c.getRepositories().stream())
                    .map(r -> RepositoryUtils.toRemoteRepository(r.getId(), r.getUrl()))
                    .collect(Collectors.toList());
            throw new ArtifactResolutionException(e, repositories, mavenSessionManager.isOffline());
        }

        writeProsperoMetadata(installDir, galleonEnv.getRepositoryManager(), channels);

        try {
            final GalleonFeaturePackAnalyzer galleonFeaturePackAnalyzer = new GalleonFeaturePackAnalyzer(galleonEnv.getChannels(), mavenSessionManager);

            try {
                // all agreements are implicitly accepted at this point
                licenseManager.recordAgreements(getPendingLicenses(provisioningConfig, galleonFeaturePackAnalyzer), installDir);
            } catch (IOException e) {
                throw Messages.MESSAGES.unableToWriteFile(installDir.resolve(LicenseManager.LICENSES_FOLDER), e);
            }

            galleonFeaturePackAnalyzer.cacheGalleonArtifacts(installDir, provisioningConfig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * List agreements and licenses that need to be accepted before installing the required Feature Packs.
     *
     * @param provisioningConfig - list of Feature Packs being installed
     * @param channels - list of channels used to resolve the Feature Packs
     * @return - list of {@code License}, or empty list if no licenses were required
     */
    public List<License> getPendingLicenses(ProvisioningConfig provisioningConfig, List<Channel> channels) {
        Objects.requireNonNull(provisioningConfig);
        Objects.requireNonNull(channels);

        final GalleonFeaturePackAnalyzer exporter = new GalleonFeaturePackAnalyzer(channels, mavenSessionManager);
        return getPendingLicenses(provisioningConfig, exporter);
    }

    private List<License> getPendingLicenses(ProvisioningConfig provisioningConfig, GalleonFeaturePackAnalyzer exporter) {
        try {
            final List<String> featurePacks = exporter.getFeaturePacks(provisioningConfig);
            return licenseManager.getLicenses(featurePacks);
        } catch (IOException | ProvisioningException | OperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, List<Channel> channels) throws MetadataException {
        final ChannelManifest manifest = maven.resolvedChannel();

        try (final InstallationMetadata installationMetadata = new InstallationMetadata(home, manifest, channels, mvnOptions)) {
            installationMetadata.recordProvision(true);
        }
    }

    private List<Channel> enforceChannelNames(List<Channel> newChannels) {
        final AtomicInteger channelCounter = new AtomicInteger(0);
        return newChannels.stream().map(c->{
            if (StringUtils.isEmpty(c.getName())) {
                return new Channel(c.getSchemaVersion(), CHANNEL_NAME_PREFIX + channelCounter.getAndIncrement(), c.getDescription(),
                        c.getVendor(), c.getRepositories(),
                        c.getManifestCoordinate(), c.getBlocklistCoordinate(), c.getNoStreamStrategy());
            } else {
                return c;
            }
        }).collect(Collectors.toList());
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