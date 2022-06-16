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

package org.wildfly.prospero.api;

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

import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProvisioningRecord;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;

public class ProvisioningDefinition {

    private static final String REPO_TYPE = "default";

    private final String fpl;
    private List<ChannelRef> channels;
    private final Set<String> includedPackages = new HashSet<>();
    private static final Map<String, String> CHANNEL_URLS = new HashMap<>();
    private final List<RemoteRepository> repositories;
    private Path definition;

    static {
        CHANNEL_URLS.put("mrrc", "https://maven.repository.redhat.com/ga/");
        CHANNEL_URLS.put("central", "https://repo1.maven.org/maven2/");
    }

    private ProvisioningDefinition(Builder builder) throws ArtifactResolutionException {
        final Optional<String> fpl = Optional.ofNullable(builder.fpl);
        final Optional<Path> definition = Optional.ofNullable(builder.definitionFile);
        final Optional<String> channelRepo = Optional.ofNullable(builder.channelRepo);
        final Optional<Path> channelsFile = Optional.ofNullable(builder.channelsFile);
        final Optional<URL> channel = Optional.ofNullable(builder.channel);
        final Optional<Set<String>> includedPackages = Optional.ofNullable(builder.includedPackages);
        this.repositories = builder.repositories==null?new ArrayList<>():new ArrayList<>(builder.repositories);

        this.includedPackages.addAll(includedPackages.orElse(Collections.emptySet()));

        try {
            if (fpl.isPresent() && (fpl.get().equals("eap") || fpl.get().equals("eap-7.4"))) {
                this.fpl = "org.jboss.eap:wildfly-ee-galleon-pack";
                this.includedPackages.add("docs.examples.configs");
                final String repoId = "mrrc";
                final String channelGA = "org.wildfly.channels:eap-74:7.4";

                setUpBuildEnv(channelRepo, channelsFile, channel, repoId, channelGA);
            } else if (fpl.isPresent() && fpl.get().equals("wildfly")) {
                this.fpl = "wildfly-core@maven(org.jboss.universe:community-universe):current";
                final String repoId = "central";
                final String channelGA = "org.wildfly.channels:wildfly:26.1.0";

                setUpBuildEnv(channelRepo, channelsFile, channel, repoId, channelGA);
            } else {
                this.fpl = fpl.orElse(null);
                this.definition = definition.orElse(null);
                final ProvisioningRecord record = ProvisioningRecord.readChannels(channelsFile.get());
                this.channels = record.getChannels();
                this.repositories.clear();
                this.repositories.addAll(record.getRepositories().stream().map(r->r.toRemoteRepository()).collect(Collectors.toList()));
            }
        } catch (IOException e) {
            throw new ArtifactResolutionException("Unable to resolve channel definition", e);
        }
    }

    private void setUpBuildEnv(Optional<String> channelRepo, Optional<Path> channelsFile, Optional<URL> channel, String repoId, String channelGA) throws IOException {
        if (!channelsFile.isPresent() && !channel.isPresent()) {
            final String repoUrl = CHANNEL_URLS.get(repoId);
            this.channels = Arrays.asList(new ChannelRef(channelGA, null));
            if (channelRepo.isPresent()) {
                String[] urls = channelRepo.get().split(",");
                for (int i = 0; i < urls.length; i++) {
                    String channelRepoId = "channel-" + (i + 1);
                    this.repositories.add(
                            new RemoteRepository.Builder(channelRepoId, REPO_TYPE, channelRepo.get()).build());
                }
            }
            repositories.add(new RemoteRepository.Builder(repoId, REPO_TYPE, repoUrl).build());
        } else if (channel.isPresent()) {
            this.channels = Arrays.asList(new ChannelRef(null, channel.get().toString()));
        } else {
            final ProvisioningRecord record = ProvisioningRecord.readChannels(channelsFile.get());
            this.channels = record.getChannels();
            this.repositories.clear();
            this.repositories.addAll(record.getRepositories().stream().map(r -> r.toRemoteRepository()).collect(Collectors.toList()));
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

    public Path getDefinition() {
        return definition;
    }

    public static class Builder {
        private String fpl;
        private Path channelsFile;
        private Path definitionFile;
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

        public Builder setDefinitionFile(Path provisionDefinition) {
            this.definitionFile = provisionDefinition;
            return this;
        }
    }
}
