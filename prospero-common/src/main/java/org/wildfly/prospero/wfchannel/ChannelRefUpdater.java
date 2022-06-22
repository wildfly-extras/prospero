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

package org.wildfly.prospero.wfchannel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

public class ChannelRefUpdater {

    private final MavenSessionManager mavenSessionManager;

    public ChannelRefUpdater(MavenSessionManager mavenSessionManager) {
        this.mavenSessionManager = mavenSessionManager;
    }

    private ChannelRef resolveLatest(ChannelRef channelRef, List<RemoteRepository> repositories) throws ArtifactResolutionException {
        if (channelRef.getGav() != null && !repositories.isEmpty()) {

            String groupId = channelRef.getGav().split(":")[0];
            String artifactId = channelRef.getGav().split(":")[1];
            String version = channelRef.getGav().split(":")[2];
            final Artifact resolvedChannelArtifact = resolveChannelFile(
                    new DefaultArtifact(groupId, artifactId, "channel", "yaml", "[" + version + ",)"),
                    repositories);
            final String fileUrl = resolvedChannelArtifact.getFile().toURI().toString();

            String newGav = String.format("%s:%s:%s", resolvedChannelArtifact.getGroupId(), resolvedChannelArtifact.getArtifactId(), resolvedChannelArtifact.getVersion());
            return new ChannelRef(newGav, fileUrl);
        } else {
            return channelRef;
        }
    }

    public List<ChannelRef> resolveLatest(List<ChannelRef> channelRefs, List<RemoteRepository> repositories) throws ArtifactResolutionException {
        final ChannelRefUpdater channelRefUpdater = new ChannelRefUpdater(mavenSessionManager);
        final List<ChannelRef> updatedRefs = new ArrayList<>();
        for (ChannelRef channelRef : channelRefs) {
            updatedRefs.add(channelRefUpdater.resolveLatest(channelRef, repositories));
        }
        return updatedRefs;
    }

    private Artifact resolveChannelFile(DefaultArtifact artifact,
                                        List<RemoteRepository> repositories) throws ArtifactResolutionException {
        final RepositorySystem repositorySystem = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession repositorySession = mavenSessionManager.newRepositorySystemSession(repositorySystem, false);

        final VersionRangeRequest request = new VersionRangeRequest();
        request.setArtifact(artifact);
        request.setRepositories(repositories);
        final VersionRangeResult versionRangeResult;
        try {
            versionRangeResult = repositorySystem.resolveVersionRange(repositorySession, request);
        } catch (VersionRangeResolutionException e) {
            throw new ArtifactResolutionException("Unable to resolve versions for " + artifact, e);
        }
        // TODO: pick latest version using Comparator
        if (versionRangeResult.getHighestVersion() == null && versionRangeResult.getVersions().isEmpty()) {
            final String delimiter = "\n  * ";
            String remoteRepositoriesString = request.getRepositories()
                    .stream()
                    .map(RemoteRepository::getUrl)
                    .collect(Collectors.joining(delimiter, delimiter, ""));
            String localRepositoryString = this.mavenSessionManager.getProvisioningRepo().toString();
            throw new ArtifactResolutionException(
                    String.format("Unable to resolve versions of %s\n  remote repositories: %s\n  local repository: %s",
                            artifact, remoteRepositoriesString, localRepositoryString));
        }
        final Artifact latestArtifact = artifact.setVersion(versionRangeResult.getHighestVersion().toString());

        final ArtifactRequest artifactRequest = new ArtifactRequest(latestArtifact, repositories, null);
        try {
            return repositorySystem.resolveArtifact(repositorySession, artifactRequest).getArtifact();
        } catch (org.eclipse.aether.resolution.ArtifactResolutionException e) {
            throw new ArtifactResolutionException("Unable to resolve " + artifact, e);
        }
    }

}
