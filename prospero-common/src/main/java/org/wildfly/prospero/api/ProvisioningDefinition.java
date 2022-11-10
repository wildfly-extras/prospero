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

package org.wildfly.prospero.api;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.galleon.FeaturePackLocationParser;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.KnownFeaturePack;
import org.wildfly.prospero.model.ProsperoConfig;

public class ProvisioningDefinition {

    public static final RepositoryPolicy DEFAULT_REPOSITORY_POLICY = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_FAIL);

    private final String fpl;
    private List<Channel> channels = new ArrayList<>();
    private final Set<String> includedPackages = new HashSet<>();
    private final List<RemoteRepository> repositories = new ArrayList<>();
    private final URI definition;

    private ProvisioningDefinition(Builder builder) throws ArtifactResolutionException, NoChannelException {
        final Optional<String> fpl = Optional.ofNullable(builder.fpl);
        final Optional<URI> definition = Optional.ofNullable(builder.definitionFile);
        final List<String> overrideRepos = builder.overrideRepositories;
        final Optional<Path> provisionConfigFile = Optional.ofNullable(builder.provisionConfigFile);
        final Optional<ChannelManifestCoordinate> manifest = Optional.ofNullable(builder.manifest);
        final Optional<Set<String>> includedPackages = Optional.ofNullable(builder.includedPackages);

        this.includedPackages.addAll(includedPackages.orElse(Collections.emptySet()));

        try {
            if (fpl.isPresent() && KnownFeaturePacks.isWellKnownName(fpl.get())) {
                KnownFeaturePack featurePackInfo = KnownFeaturePacks.getByName(fpl.get());
                this.fpl = null;
                this.definition = featurePackInfo.getGalleonConfiguration();
                this.repositories.addAll(featurePackInfo.getRemoteRepositories());
                setUpBuildEnv(overrideRepos, provisionConfigFile, manifest, featurePackInfo.getChannels());
            } else if (provisionConfigFile.isPresent()) {
                this.fpl = fpl.orElse(null);
                this.definition = definition.orElse(null);
                this.channels = ProsperoConfig.readConfig(provisionConfigFile.get()).getChannels();
                this.repositories.clear();
                this.repositories.addAll(Collections.emptyList());
            } else {
                // TODO: provisionConfigFile needn't be mandatory, we could still collect all required data from the
                //  other options (channel, channelRepo - perhaps both should be made collections)
                throw new IllegalArgumentException(
                        String.format("Incomplete configuration: either a predefined fpl (%s) or a provisionConfigFile must be given.",
                                String.join(", ", KnownFeaturePacks.getNames())));
            }
        } catch (IOException e) {
            throw new ArtifactResolutionException("Unable to resolve channel definition: " + e.getMessage(), e);
        }

        if (channels.isEmpty() || channelsMissingManifest()) {
            if (fpl.isPresent() && KnownFeaturePacks.isWellKnownName(fpl.get())) {
                throw Messages.MESSAGES.fplDefinitionDoesntContainChannel(fpl.get());
            } else {
                throw Messages.MESSAGES.noChannelReference();
            }
        }
    }

    private boolean channelsMissingManifest() {
        return channels.stream().anyMatch(c->c.getManifestRef() == null);
    }

    private void setUpBuildEnv(List<String> additionalRepositories, Optional<Path> provisionConfigFile,
                               Optional<ChannelManifestCoordinate> manifestRef, List<Channel> channels) throws IOException {
        if (!provisionConfigFile.isPresent() && !manifestRef.isPresent()) {
            if (!additionalRepositories.isEmpty()) {
                this.channels = TemporaryRepositoriesHandler.addRepositories(channels,
                        TemporaryRepositoriesHandler.from(additionalRepositories));
            } else {
                this.channels = channels;
            }
        } else if (manifestRef.isPresent()) {
            final List<Repository> repos;

            final Channel channel = new Channel("", "", null, null,
                    new ArrayList<>(channels.stream().flatMap(c -> c.getRepositories().stream()).collect(Collectors.toSet())), manifestRef.get());

            this.channels = TemporaryRepositoriesHandler.addRepositories(List.of(channel),
                    TemporaryRepositoriesHandler.from(additionalRepositories));
        } else {
            this.channels = ProsperoConfig.readConfig(provisionConfigFile.get()).getChannels();
            this.repositories.clear();
            this.repositories.addAll(Collections.emptyList());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getFpl() {
        return fpl;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    public URI getDefinition() {
        return definition;
    }

    public ProsperoConfig getProsperoConfig() {
        return new ProsperoConfig(channels);
    }

    public ProvisioningConfig toProvisioningConfig() throws ProvisioningException {
        if (fpl != null) {
            FeaturePackLocation loc = FeaturePackLocationParser.resolveFpl(getFpl());

            final FeaturePackConfig.Builder configBuilder = FeaturePackConfig.builder(loc);
            for (String includedPackage : includedPackages) {
                configBuilder.includePackage(includedPackage);
            }
            return ProvisioningConfig.builder().addFeaturePackDep(configBuilder.build()).build();
        } else if (definition != null) {
            try {
                return GalleonUtils.loadProvisioningConfig(definition);
            } catch (XMLStreamException e) {
                throw new ProvisioningException("Can't parse the Galleon provisioning file.", e);
            }
        } else {
            throw new IllegalArgumentException("Can't create ProvisioningConfig: Neither feature-pack-location nor ProvisioningConfig path set.");
        }
    }

    public static class Builder {
        private String fpl;
        private Path provisionConfigFile;
        private URI definitionFile;
        private List<String> overrideRepositories = Collections.emptyList();
        private Set<String> includedPackages;
        private ChannelManifestCoordinate manifest;

        public ProvisioningDefinition build() throws ArtifactResolutionException, NoChannelException {
            return new ProvisioningDefinition(this);
        }

        public Builder setFpl(String fpl) {

            this.fpl = fpl;
            return this;
        }

        public Builder setProvisionConfig(Path provisionConfigFile) {
            this.provisionConfigFile = provisionConfigFile;
            return this;
        }

        public Builder setOverrideRepositories(List<String> repositories) {
            this.overrideRepositories = repositories;
            return this;
        }

        public Builder setIncludedPackages(Set<String> includedPackages) {
            this.includedPackages = includedPackages;
            return this;
        }

        public Builder setManifest(String manifest) {
            if (manifest != null) {
                this.manifest = ArtifactUtils.manifestFromString(manifest);
            }
            return this;
        }

        public Builder setDefinitionFile(URI provisionDefinition) {
            this.definitionFile = provisionDefinition;
            return this;
        }
    }
}
