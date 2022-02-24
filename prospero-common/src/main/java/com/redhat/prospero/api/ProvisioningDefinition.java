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
               final String repoUrl = channelRepo.orElse(CHANNEL_URLS.get("mrrc"));
               this.channels = Arrays.asList(new ChannelRef("mrrc", repoUrl, "org.wildfly.channels:eap-74:7.4", null));
            } else {
               this.channels = ChannelRef.readChannels(channelsFile.get());
            }
         } else if (fpl.equals("wildfly")) {
            this.fpl = "org.wildfly:wildfly-ee-galleon-pack";

            if (!channelsFile.isPresent()) {
               final String repoUrl = channelRepo.orElse(CHANNEL_URLS.get("central"));
               this.channels = Arrays.asList(new ChannelRef("central", repoUrl, "org.wildfly.channels:wildfly:26.1.0", null));
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
