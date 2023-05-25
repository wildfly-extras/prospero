/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.util.PathsUtils;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonFeaturePackAnalyzer;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.metadata.ManifestVersionResolver;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.updates.MarkerFile;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

class PrepareCandidateAction implements AutoCloseable{

    private final InstallationMetadata metadata;
    private final Path installDir;
    private final ProsperoConfig prosperoConfig;
    private final MavenSessionManager mavenSessionManager;

    PrepareCandidateAction(Path installDir, MavenSessionManager mavenSessionManager, ProsperoConfig prosperoConfig)
            throws OperationException {
        this.metadata = InstallationMetadata.loadInstallation(installDir);
        this.installDir = installDir;
        this.prosperoConfig = prosperoConfig;
        this.mavenSessionManager = mavenSessionManager;
    }

    boolean buildCandidate(Path targetDir, GalleonEnvironment galleonEnv, ApplyCandidateAction.Type operation) throws ProvisioningException, OperationException {
        doBuildUpdate(targetDir, galleonEnv);

        try {
            final SavedState savedState = metadata.getRevisions().get(0);
            new MarkerFile(savedState.getName(), operation).write(targetDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    private void doBuildUpdate(Path targetDir, GalleonEnvironment galleonEnv) throws ProvisioningException, OperationException {
        final ProvisioningManager provMgr = galleonEnv.getProvisioningManager();
        final ProvisioningConfig provisioningConfig = ProvisioningXmlParser.parse(PathsUtils.getProvisioningXml(installDir));
        try {
            GalleonUtils.executeGalleon((options) -> {
                        options.put(Constants.EXPORT_SYSTEM_PATHS, "true");
                        provMgr.provision(provisioningConfig, options);
                    },
                    mavenSessionManager.getProvisioningRepo().toAbsolutePath());
        } catch (UnresolvedMavenArtifactException e) {
            throw new ArtifactResolutionException(ProsperoLogger.ROOT_LOGGER.unableToResolve(), e, e.getUnresolvedArtifacts(),
                    e.getAttemptedRepositories(), mavenSessionManager.isOffline());
        }

        try {
            final ManifestVersionRecord manifestRecord =
                    new ManifestVersionResolver(mavenSessionManager.getProvisioningRepo(), mavenSessionManager.newRepositorySystem())
                            .getCurrentVersions(galleonEnv.getChannels());
            writeProsperoMetadata(targetDir, galleonEnv.getRepositoryManager(), prosperoConfig.getChannels(),
                    manifestRecord);
        } catch (IOException ex) {
            throw ProsperoLogger.ROOT_LOGGER.unableToDownloadFile(ex);
        }

        try {
            final GalleonFeaturePackAnalyzer galleonFeaturePackAnalyzer = new GalleonFeaturePackAnalyzer(galleonEnv.getChannels(), mavenSessionManager);

            galleonFeaturePackAnalyzer.cacheGalleonArtifacts(targetDir, provisioningConfig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        metadata.close();
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, List<Channel> channels, ManifestVersionRecord manifestVersions) throws MetadataException {
        final ChannelManifest manifest = maven.resolvedChannel();

        try (InstallationMetadata installationMetadata = InstallationMetadata.newInstallation(home, manifest,
                new ProsperoConfig(channels), Optional.of(manifestVersions))) {
            installationMetadata.recordProvision(true, false);
        }
    }
}
