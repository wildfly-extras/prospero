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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.redhat.prospero.api.exceptions.ArtifactResolutionException;
import com.redhat.prospero.model.ChannelRef;
import com.redhat.prospero.model.ProvisioningRecord;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;

public class ProvisioningDefinition {

    private final String fpl;
    private final List<ChannelRef> channels;
    private final Set<String> includedPackages = new HashSet<>();
    private static final Map<String, String> CHANNEL_URLS = new HashMap<>();
    private final List<RemoteRepository> repositories;

    static {
        CHANNEL_URLS.put("mrrc", "https://maven.repository.redhat.com/ga/");
        CHANNEL_URLS.put("central", "https://repo1.maven.org/maven2/");
    }


    private ProvisioningDefinition(Builder builder) throws ArtifactResolutionException {
        final String fpl = builder.fpl;
        final Optional<String> channelRepo = Optional.ofNullable(builder.channelRepo);
        final Optional<Path> channelsFile = Optional.ofNullable(builder.channelsFile);
        final Optional<URL> channel = Optional.ofNullable(builder.channel);
        final Optional<Set<String>> includedPackages = Optional.ofNullable(builder.includedPackages);
        this.repositories = builder.repositories==null?new ArrayList<>():new ArrayList<>(builder.repositories);

        this.includedPackages.addAll(includedPackages.orElse(Collections.emptySet()));

        try {
            if (fpl.equals("eap") || fpl.equals("eap-7.4")) {
                this.fpl = "org.jboss.eap:wildfly-ee-galleon-pack";
                this.includedPackages.add("docs.examples.configs");

                if (!channelsFile.isPresent() && !channel.isPresent()) {
                    final String repoUrl = CHANNEL_URLS.get("mrrc");
                    this.channels = Arrays.asList(new ChannelRef("org.wildfly.channels:eap-74:7.4", null));
                    if (channelRepo.isPresent()) {
                        this.repositories.add(new RemoteRepository.Builder("channel", "default", channelRepo.get()).build());
                    }
                    repositories.add(new RemoteRepository.Builder("mrrc", "default", repoUrl).build());
                } else if (channel.isPresent()) {
                    this.channels = Arrays.asList(new ChannelRef(null, channel.get().toString()));
                } else {
                    final ProvisioningRecord record = ProvisioningRecord.readChannels(channelsFile.get());
                    this.channels = record.getChannels();
                    this.repositories.clear();
                    this.repositories.addAll(record.getRepositories().stream().map(r->r.toRemoteRepository()).collect(Collectors.toList()));
                }
            } else if (fpl.equals("wildfly")) {
                this.fpl = "wildfly-core@maven(org.jboss.universe:community-universe):current";

                if (!channelsFile.isPresent() && !channel.isPresent()) {
                    final String repoUrl = CHANNEL_URLS.get("central");
                    this.channels = Arrays.asList(new ChannelRef("org.wildfly.channels:wildfly:26.1.0", null));
                    if (channelRepo.isPresent()) {
                        this.repositories.add(new RemoteRepository.Builder("channel", "default", channelRepo.get()).build());
                    }
                    repositories.add(new RemoteRepository.Builder("mrrc", "default", repoUrl).build());
                } else if (channel.isPresent()) {
                    this.channels = Arrays.asList(new ChannelRef(null, channel.get().toString()));
                } else {
                    final ProvisioningRecord record = ProvisioningRecord.readChannels(channelsFile.get());
                    this.channels = record.getChannels();
                    this.repositories.clear();
                    this.repositories.addAll(record.getRepositories().stream().map(r->r.toRemoteRepository()).collect(Collectors.toList()));
                }
            } else {
                this.fpl = fpl;
                final ProvisioningRecord record = ProvisioningRecord.readChannels(channelsFile.get());
                this.channels = record.getChannels();
                this.repositories.clear();
                this.repositories.addAll(record.getRepositories().stream().map(r->r.toRemoteRepository()).collect(Collectors.toList()));
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

    public List<RemoteRepository> getRepositories() {
        return repositories;
    }

    public static class Builder {
        private String fpl;
        private Path channelsFile;
        private String channelRepo;
        private Set<String> includedPackages;
        private URL channel;
        private List<RemoteRepository> repositories;

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

        public Builder setChannel(String channel) throws ProvisioningException {
            if (channel != null) {
                try {
                    this.channel = new URL(channel);
                } catch (MalformedURLException e) {
                    try {
                        this.channel = Paths.get(channel).toAbsolutePath().toUri().toURL();
                    } catch (MalformedURLException ex) {
                        throw new ProvisioningException("Unrecognized path to channels file", ex);
                    }
                }
            }
            return this;
        }

        public Builder setRepositories(List<RemoteRepository> repositories) {
            this.repositories = repositories;
            return this;
        }
    }
}
