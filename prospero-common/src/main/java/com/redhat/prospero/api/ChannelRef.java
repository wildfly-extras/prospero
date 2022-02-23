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

package com.redhat.prospero.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.redhat.prospero.api.exceptions.ArtifactResolutionException;
import com.redhat.prospero.wfchannel.WfChannelMavenResolver;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

public class ChannelRef {

    public static void writeChannels(List<ChannelRef> channelRefs, File channelsFile) throws IOException {
        new ObjectMapper(new YAMLFactory()).writeValue(channelsFile, channelRefs);
    }

    public static List<ChannelRef> readChannels(Path path) throws IOException, ArtifactResolutionException {
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, ChannelRef.class);
        final List<ChannelRef> channelRefs = objectMapper.readValue(path.toUri().toURL(), type);
        for (ChannelRef channelRef : channelRefs) {
            if (channelRef.gav != null && channelRef.repoUrl != null) {
                // resolve new version
                String groupId = channelRef.gav.split(":")[0];
                String artifactId = channelRef.gav.split(":")[1];
                String version = channelRef.gav.split(":")[2];
                final String fileUrl = resolveChannelFile(new DefaultArtifact(groupId, artifactId, "channel", "yaml", "[" + version + ",)"),
                                                                                 new RemoteRepository.Builder(channelRef.getName(), "default", channelRef.getRepoUrl()).build())
                   .getFile().toURI().toURL().toString();
                channelRef.setUrl(fileUrl);
            }
        }

        return channelRefs;
    }

    private static Artifact resolveChannelFile(DefaultArtifact artifact,
                                              RemoteRepository repo) throws ArtifactResolutionException {
        final RepositorySystem repositorySystem = WfChannelMavenResolver.newRepositorySystem();
        final DefaultRepositorySystemSession repositorySession = WfChannelMavenResolver.newRepositorySystemSession(repositorySystem, true, null);

        final VersionRangeRequest request = new VersionRangeRequest();
        request.setArtifact(artifact);
        request.setRepositories(Arrays.asList(repo));
        final VersionRangeResult versionRangeResult;
        try {
            versionRangeResult = repositorySystem.resolveVersionRange(repositorySession, request);
        } catch (VersionRangeResolutionException e) {
            throw new ArtifactResolutionException("Unable to resolve versions for " + artifact, e);
        }
        // TODO: pick latest version using Comparator
        if (versionRangeResult.getHighestVersion() == null && versionRangeResult.getVersions().isEmpty()) {
            throw new ArtifactResolutionException(
               String.format("Unable to resolve versions of %s in repository [%s: %s]", artifact, repo.getId(), repo.getUrl()));
        }
        final Artifact latestArtifact = artifact.setVersion(versionRangeResult.getHighestVersion().toString());

        final ArtifactRequest artifactRequest = new ArtifactRequest(latestArtifact, Arrays.asList(repo), null);
        try {
            return repositorySystem.resolveArtifact(repositorySession, artifactRequest).getArtifact();
        } catch (org.eclipse.aether.resolution.ArtifactResolutionException e) {
            throw new ArtifactResolutionException("Unable to resolve " + artifact, e);
        }
    }

    private String name;

    private String url;

    private String repoUrl;

    private String gav;

    public ChannelRef() {

    }

    public ChannelRef(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public ChannelRef(String name, String repoUrl, String gav) throws ArtifactResolutionException {
        this.name = name;
        this.repoUrl = repoUrl;
        this.gav = gav;
        // get latest version of gav
        this.url = resolveUrlFromGav(gav, repoUrl);
    }

    private static String resolveUrlFromGav(String gav, String repoUrl) throws ArtifactResolutionException {
        String groupId = gav.split(":")[0];
        String artifactId = gav.split(":")[1];
        String version = gav.split(":")[2];
        final Artifact channelArtifact = resolveChannelFile(new DefaultArtifact(groupId, artifactId, "channel", "yaml", "[" + version + ",)"),
                                                  new RemoteRepository.Builder("channel-repo", "default", repoUrl).build());
        return channelArtifact.getFile().toURI().toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getGav() {
        return gav;
    }

    public void setGav(String gav) {
        this.gav = gav;
    }

    @Override
    public String toString() {
        return "Channel{" + "name='" + name + '\'' + ", url='" + url + '\'' + '}';
    }
}
