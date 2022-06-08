/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.actions;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.wildfly.prospero.api.ArtifactUtils;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.wfchannel.ChannelRefUpdater;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.wildfly.prospero.wfchannel.WfChannelMavenResolverFactory;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.jboss.galleon.layout.ProvisioningPlan;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.UnresolvedMavenArtifactException;

import static org.wildfly.prospero.galleon.GalleonUtils.MAVEN_REPO_LOCAL;

public class Update {

    public static final int UPDATES_SEARCH_PARALLELISM = 10;
    private final InstallationMetadata metadata;
    private final ChannelMavenArtifactRepositoryManager maven;
    private final ProvisioningManager provMgr;
    private final ChannelSession channelSession;

    private final Console console;
    private final WfChannelMavenResolverFactory factory;
    private final MavenSessionManager mavenSessionManager;

    public Update(Path installDir, MavenSessionManager mavenSessionManager, Console console) throws ProvisioningException, OperationException {
        this.metadata = new InstallationMetadata(installDir);

        this.mavenSessionManager = mavenSessionManager;
        final List<Channel> channels = mapToChannels(new ChannelRefUpdater(this.mavenSessionManager)
                .resolveLatest(metadata.getChannels(), metadata.getRepositories()));
        final List<RemoteRepository> repositories = metadata.getRepositories();

        this.factory = new WfChannelMavenResolverFactory(mavenSessionManager, repositories);
        this.channelSession = new ChannelSession(channels, factory);
        this.maven = new ChannelMavenArtifactRepositoryManager(channelSession);
        this.provMgr = GalleonUtils.getProvisioningManager(installDir, maven);
        final ProvisioningLayoutFactory layoutFactory = provMgr.getLayoutFactory();

        layoutFactory.setProgressCallback("LAYOUT_BUILD", console.getProgressCallback("LAYOUT_BUILD"));
        layoutFactory.setProgressCallback("PACKAGES", console.getProgressCallback("PACKAGES"));
        layoutFactory.setProgressCallback("CONFIGS", console.getProgressCallback("CONFIGS"));
        layoutFactory.setProgressCallback("JBMODULES", console.getProgressCallback("JBMODULES"));
        this.console = console;
    }

    private List<Channel> mapToChannels(List<ChannelRef> channelRefs) throws MetadataException {
        final List<Channel> channels = new ArrayList<>();
        for (ChannelRef ref : channelRefs) {
            try {
                channels.add(ChannelMapper.from(new URL(ref.getUrl())));
            } catch (MalformedURLException e) {
                throw new MetadataException("Unable to resolve channel configuration", e);
            }
        }
        return channels;
    }

    public void doUpdateAll() throws ProvisioningException, MetadataException, ArtifactResolutionException {
        final UpdateSet updateSet = findUpdates();

        console.updatesFound(updateSet.fpUpdates.getUpdates(), updateSet.artifactUpdates);
        if (updateSet.isEmpty()) {
            return;
        }

        if (!console.confirmUpdates()) {
            return;
        }

        applyFpUpdates(updateSet.fpUpdates);

        metadata.writeFiles();

        console.updatesComplete();
    }

    public void listUpdates() throws ArtifactResolutionException, ProvisioningException {
        final UpdateSet updateSet = findUpdates();

        console.updatesFound(updateSet.fpUpdates.getUpdates(), updateSet.artifactUpdates);
    }

    private UpdateSet findUpdates() throws ArtifactResolutionException, ProvisioningException {

        // use parallel executor to speed up the artifact resolution
        final ExecutorService executorService = Executors.newWorkStealingPool(UPDATES_SEARCH_PARALLELISM);
        List<CompletableFuture<Optional<ArtifactChange>>> allPackages = new ArrayList<>();
        for (Artifact artifact : metadata.getArtifacts()) {
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

        executorService.shutdown();

        final List<ArtifactChange> updates = allPackages.stream()
                .map(cf ->cf.getNow(Optional.empty()))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        final ProvisioningPlan fpUpdates = findFPUpdates();
        return new UpdateSet(fpUpdates, updates);
    }

    private Optional<ArtifactChange> findUpdates(Artifact artifact) throws ArtifactResolutionException {
        if (artifact == null) {
            throw new ArtifactResolutionException(String.format("Artifact [%s:%s] not found", artifact.getGroupId(), artifact.getArtifactId()));
        }

        final String latestVersion;
        try {
            latestVersion = channelSession.findLatestMavenArtifactVersion(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier());
        } catch (UnresolvedMavenArtifactException e) {
            throw new ArtifactResolutionException(String.format("Artifact [%s:%s] not found", artifact.getGroupId(), artifact.getArtifactId()), e);
        }
        final Artifact latest = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), latestVersion);


        if (latestVersion == null || ArtifactUtils.compareVersion(latest, artifact) <= 0) {
            return Optional.empty();
        } else {
            return Optional.of(new ArtifactChange(artifact, latest));
        }
    }

    private ProvisioningPlan findFPUpdates() throws ProvisioningException {
        return provMgr.getUpdates(true);
    }

    private void applyFpUpdates(ProvisioningPlan updates) throws ProvisioningException {
        try {
            System.setProperty(MAVEN_REPO_LOCAL, mavenSessionManager.getProvisioningRepo().toAbsolutePath().toString());
            final HashMap<String, String> options = new HashMap<>();
            options.put(GalleonUtils.JBOSS_FORK_EMBEDDED_PROPERTY, GalleonUtils.JBOSS_FORK_EMBEDDED_VALUE);
            provMgr.apply(updates, options);
        } finally {
            System.clearProperty(MAVEN_REPO_LOCAL);
        }

        metadata.setChannel(maven.resolvedChannel());
    }

    private class UpdateSet {

        private final ProvisioningPlan fpUpdates;
        private final List<ArtifactChange> artifactUpdates;

        public UpdateSet(ProvisioningPlan fpUpdates, List<ArtifactChange> updates) {
            this.fpUpdates = fpUpdates;
            this.artifactUpdates = updates;
        }

        public boolean isEmpty() {
            return fpUpdates.getUpdates().isEmpty() && artifactUpdates.isEmpty();
        }
    }
}
