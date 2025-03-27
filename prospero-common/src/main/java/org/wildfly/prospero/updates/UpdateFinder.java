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

package org.wildfly.prospero.updates;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.channel.RuntimeChannel;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.VersionResult;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.ChannelVersionChange;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.metadata.ManifestVersionRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class UpdateFinder implements AutoCloseable {

    public static final int UPDATES_SEARCH_PARALLELISM = 10;

    private final ChannelSession channelSession;
    private final ExecutorService executorService;
    private final InstallationMetadata metadata;

    public UpdateFinder(ChannelSession channelSession, InstallationMetadata metadata) {
        this.channelSession = channelSession;
        this.executorService = Executors.newWorkStealingPool(UPDATES_SEARCH_PARALLELISM);
        this.metadata = metadata;
    }

    public UpdateSet findUpdates(List<Artifact> artifacts) throws ArtifactResolutionException {
        // use parallel executor to speed up the artifact resolution
        List<CompletableFuture<Optional<ArtifactChange>>> allPackages = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            final CompletableFuture<Optional<ArtifactChange>> cf = new CompletableFuture<>();
            executorService.submit(() -> {
                try {
                    final Optional<ArtifactChange> found = findUpdates(artifact);
                    cf.complete(found);
                } catch (Exception e) {
                    cf.completeExceptionally(e);
                }
            });
            allPackages.add(cf);
        }

        try {
            CompletableFuture.allOf(allPackages.toArray(new CompletableFuture[]{})).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof ArtifactResolutionException) {
                throw (ArtifactResolutionException) e.getCause();
            } else {
                throw e;
            }
        }

        final List<ArtifactChange> updates = allPackages.stream()
                .map(cf ->cf.getNow(Optional.empty()))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        return new UpdateSet(updates, findManifestUpdates(), areManifestVersionsAuthoritative());
    }

    private Optional<ArtifactChange> findUpdates(Artifact artifact) throws ArtifactResolutionException {

        final String latestVersion;
        final Optional<String> channelName;
        try {
            final VersionResult versionResult = channelSession.findLatestMavenArtifactVersion(artifact.getGroupId(),
                    artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), null);
            latestVersion = versionResult.getVersion();
            channelName = versionResult.getChannelName();

        } catch (UnresolvedMavenArtifactException e) {
            return Optional.of(ArtifactChange.removed(artifact));
        }
        final Artifact latest = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), latestVersion);

        if (latestVersion == null || latest.getVersion().equals(artifact.getVersion())) {
            return Optional.empty();
        } else {
            return Optional.of(ArtifactChange.updated(artifact, latest, channelName.orElse(null)));
        }
    }

    private List<ChannelVersionChange> findManifestUpdates() {
        final Optional<ManifestVersionRecord> manifestRecord = metadata.getManifestVersions();

        Map<String, ManifestVersion> currentManifests = new HashMap<>();
        for (ManifestVersionRecord.MavenManifest manifest : manifestRecord
                .map(ManifestVersionRecord::getMavenManifests)
                .orElse(Collections.emptyList())) {
            currentManifests.put(manifest.getGroupId() + ":" + manifest.getArtifactId(),
                    new ManifestVersion(manifest.getVersion(), manifest.getDescription()));
        }

        final ArrayList<ChannelVersionChange> manifestChanges = new ArrayList<>();
        final Set<String> removedGavs = currentManifests.keySet();

        for (RuntimeChannel runtimeChannel : channelSession.getRuntimeChannels()) {
            final Channel channelDefinition = runtimeChannel.getChannelDefinition();
            final ChannelManifestCoordinate manifestCoordinate = channelDefinition.getManifestCoordinate();
            if (manifestCoordinate == null || manifestCoordinate.getMaven() == null) {
                // skip the no-manifest or URL-based channel
                continue;
            }

            final MavenCoordinate newManifest = manifestCoordinate.getMaven();
            final String ga = newManifest.getGroupId() + ":" + newManifest.getArtifactId();

            final ChannelVersionChange.Builder builder = new ChannelVersionChange.Builder(channelDefinition.getName())
                    .setNewPhysicalVersion(newManifest.getVersion())
                    .setNewLogicalVersion(runtimeChannel.getChannelManifest().getName());

            if (currentManifests.containsKey(ga)) {
                final ManifestVersion oldManifest = currentManifests.get(ga);

                builder
                        .setOldPhysicalVersion(oldManifest.physicalVersion)
                        .setOldLogicalVersion(oldManifest.logicalVersion);
            }

            removedGavs.remove(ga);
            manifestChanges.add(builder.build());
        }

        for (String ga : removedGavs) {
            final ManifestVersion oldManifest = currentManifests.get(ga);
            final ChannelVersionChange.Builder builder = new ChannelVersionChange.Builder(ga)
                    .setOldPhysicalVersion(oldManifest.physicalVersion)
                    .setOldLogicalVersion(oldManifest.logicalVersion);
            manifestChanges.add(builder.build());
        }
        return manifestChanges;
    }

    /*
     * manifest updates are authoritative if all channels in the update:
     *  * are maven based (and we can get the versions)
     *  * are not using resolve-if-no-stream
     *  * are not using versionPatterns
     */
    private boolean areManifestVersionsAuthoritative() {
        if (metadata.getManifestVersions().isEmpty() || metadata.getManifestVersions().get().getMavenManifests().isEmpty()) {
            return false;
        }

        if (channelSession.getRuntimeChannels().isEmpty()) {
            return false;
        }
        for (RuntimeChannel runtimeChannel : channelSession.getRuntimeChannels()) {
            final Channel channelDefinition = runtimeChannel.getChannelDefinition();
            if (channelDefinition.getNoStreamStrategy() != Channel.NoStreamStrategy.NONE) {
                return false;
            }
            if (channelDefinition.getManifestCoordinate() != null && channelDefinition.getManifestCoordinate().getMaven() == null) {
                return false;
            }
            final ChannelManifest manifest = runtimeChannel.getChannelManifest();
            if (manifest == null) {
                return false;
            }
            if (manifest.getStreams().stream().anyMatch(s->s.getVersionPattern() != null)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void close() {
        this.executorService.shutdown();
    }

    private static class ManifestVersion {
        private final String logicalVersion;
        private final String physicalVersion;

        public ManifestVersion(String physicalVersion, String logicalVersion) {
            this.logicalVersion = logicalVersion;
            this.physicalVersion = physicalVersion;
        }
    }
}
