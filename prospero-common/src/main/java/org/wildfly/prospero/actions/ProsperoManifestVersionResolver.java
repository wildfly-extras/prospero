/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

import org.jboss.logging.Logger;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.metadata.ManifestVersionResolver;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.wildfly.prospero.wfchannel.ResolvedArtifactsStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Identifies manifests used to provision installation.
 * It uses {@code ResolvedManifestVersions} to find already resolved manifests and falls back onto
 * {@code ManifestVersionResolver} if not possible.
 */
class ProsperoManifestVersionResolver {

    private static final Logger LOG = Logger.getLogger(ProsperoManifestVersionResolver.class.getName());

    private final ResolvedArtifactsStore manifestVersions;

    private final Supplier<ManifestVersionResolver> manifestVersionResolver;

    ProsperoManifestVersionResolver(MavenSessionManager mavenSessionManager) {
        this.manifestVersions = mavenSessionManager.getResolvedArtifactVersions();
        this.manifestVersionResolver = () -> new ManifestVersionResolver(
                mavenSessionManager.getProvisioningRepo(),
                mavenSessionManager.newRepositorySystem());
    }

    ProsperoManifestVersionResolver(ResolvedArtifactsStore manifestVersions, ManifestVersionResolver manifestVersionResolver) {
        this.manifestVersions = manifestVersions;
        this.manifestVersionResolver = () -> manifestVersionResolver;
    }

    /**
     * attempt to resolve the current versions from artifacts recorded during provisioning.
     * Fallback on artifacts in the local maven cache if not available.
     *
     * @param channels
     * @return
     * @throws IOException
     */
    public ManifestVersionRecord getCurrentVersions(List<Channel> channels) throws IOException {
        final ManifestVersionRecord record = new ManifestVersionRecord();
        final ArrayList<Channel> fallbackChannels = new ArrayList<>();
        for (Channel channel : channels) {
            if (channel.getManifestCoordinate().getMaven() != null && channel.getManifestCoordinate().getMaven().getVersion() == null) {
                final MavenCoordinate manifestCoord = channel.getManifestCoordinate().getMaven();
                if (LOG.isDebugEnabled()) {
                    LOG.debugf("Trying to lookup manifest %s", manifestCoord);
                }
                final MavenArtifact version = manifestVersions.getManifestVersion(manifestCoord.getGroupId(), manifestCoord.getArtifactId());
                if (version == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debugf("Failed to lookup manifest %s in currently resolved artifacts", manifestCoord);
                    }
                    fallbackChannels.add(channel);
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debugf("Manifest %s resolved in currently resolve artifacts, recording.", manifestCoord);
                    }
                    final String description = ChannelManifestMapper.from(version.getFile().toURI().toURL()).getDescription();
                    record.addManifest(new ManifestVersionRecord.MavenManifest(
                            manifestCoord.getGroupId(),
                            manifestCoord.getArtifactId(),
                            version.getVersion(),
                            description));
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debugf("Manifest for channel %s will be resolved via fallback.", channel.getName());
                }
                fallbackChannels.add(channel);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debugf("Resolving channel manifests using fallback mechanisms.");
        }
        final ManifestVersionRecord currentVersions = manifestVersionResolver.get().getCurrentVersions(fallbackChannels);
        currentVersions.getMavenManifests().forEach(record::addManifest);
        currentVersions.getOpenManifests().forEach(record::addManifest);
        currentVersions.getUrlManifests().forEach(record::addManifest);

        return record;
    }
}
