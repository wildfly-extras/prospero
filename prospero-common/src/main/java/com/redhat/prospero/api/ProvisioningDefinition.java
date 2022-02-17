/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

public class ProvisioningDefinition {

   private final String fpl;
   private final List<ChannelRef> channels;
   private final Set<String> includedPackages = new HashSet<>();
   private static final Map<String, String> CHANNEL_URLS = new HashMap<>();
   static {
      CHANNEL_URLS.put("mrrc", "https://maven.repository.redhat.com/ga/");
      CHANNEL_URLS.put("central", "https://repo1.maven.org/maven2/");
   }

   private ProvisioningDefinition(Builder builder) throws ArtifactResolutionException {
      final String fpl = builder.fpl;
      final Optional<String> channelRepo = Optional.ofNullable(builder.channelRepo);
      final Optional<Path> channelsFile = Optional.ofNullable(builder.channelsFile);
      final Optional<Set<String>> includedPackages = Optional.ofNullable(builder.includedPackages);

      this.includedPackages.addAll(includedPackages.orElse(Collections.emptySet()));

      try {
         if (fpl.equals("eap")) {
            this.fpl = "org.jboss.eap:wildfly-ee-galleon-pack";
            this.includedPackages.add("docs.examples.configs");

            if (!channelsFile.isPresent()) {
               final DefaultArtifact artifact = new DefaultArtifact("org.wildfly.channels", "eap-74", "channel", "yaml", "[7.4,)");
               final String repoUrl = channelRepo.orElse(CHANNEL_URLS.get("mrrc"));
               final RemoteRepository repo = new RemoteRepository.Builder("mrrc", "default", repoUrl).build();
               this.channels = readLatestChannelFromMaven(artifact, repoUrl, repo);
            } else {
               this.channels = ChannelRef.readChannels(channelsFile.get());
            }
         } else if (fpl.equals("wildfly")) {
            this.fpl = "org.wildfly:wildfly-ee-galleon-pack";

            if (!channelsFile.isPresent()) {
               final DefaultArtifact artifact = new DefaultArtifact("org.wildfly.channels", "wildfly", "channel", "yaml", "[26.1.0,)");
               final String repoUrl = channelRepo.orElse(CHANNEL_URLS.get("central"));
               final RemoteRepository repo = new RemoteRepository.Builder("central", "default", repoUrl).build();
               this.channels = readLatestChannelFromMaven(artifact, repoUrl, repo);
            } else {
               this.channels = ChannelRef.readChannels(channelsFile.get());
            }
         } else {
            this.fpl = fpl;
            this.channels = ChannelRef.readChannels(channelsFile.get());
         }
      } catch (IOException e) {
         throw new ArtifactResolutionException("Unable to resolve channel definition", e);
      }
   }

   public static Builder builder() {
      return new Builder();
   }

   public Set<String> getIncludedPackages() {
      return includedPackages;
   }

   public String getFpl() {
      return fpl;
   }

   public List<ChannelRef> getChannelRefs() {
      return channels;
   }

   public static Artifact resolveChannelFile(DefaultArtifact artifact,
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

   protected List<ChannelRef> readLatestChannelFromMaven(DefaultArtifact artifact,
                                                         String repoUrl,
                                                         RemoteRepository repo) throws ArtifactResolutionException {
      try {
         final Artifact resolvedArtifact = resolveChannelFile(artifact, repo);
         final String resolvedChannelFileUrl = resolvedArtifact.getFile().toURI().toURL().toString();
         String gav = resolvedArtifact.getGroupId() + ":" + resolvedArtifact.getArtifactId() + ":" + resolvedArtifact.getVersion();
         ChannelRef channelRef = new ChannelRef("eap", repoUrl, gav, resolvedChannelFileUrl);

         return Arrays.asList(channelRef);
      } catch (IOException e) {
         throw new ArtifactResolutionException(e);
      }
   }

   public static class Builder {
      private String fpl;
      private Path channelsFile;
      private String channelRepo;
      private Set<String> includedPackages;

      public ProvisioningDefinition build() throws ArtifactResolutionException {
         return new ProvisioningDefinition(this);
      }

      public Builder setFpl(String fpl) {
         this.fpl = fpl;
         return this;
      }

      public Builder setChannelsFile(Path channelsFile) {
         this.channelsFile = channelsFile;
         return this;
      }

      public Builder setChannelRepo(String channelRepo) {
         this.channelRepo = channelRepo;
         return this;
      }

      public Builder setIncludedPackages(Set<String> includedPackages) {
         this.includedPackages = includedPackages;
         return this;
      }
   }
}
