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

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.maven.ChannelCoordinate;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.galleon.FeaturePackLocationParser;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.KnownFeaturePack;
import org.wildfly.prospero.model.ProsperoConfig;

public class ProvisioningDefinition {

    /**
     * Galleon feature pack location. Can be either a well-known name (like "eap-8.0" or "wildfly") that references a predefined
     * combination of Galleon configuration and channels, a feature pack G:A(:V), or a standard Galleon feature pack location
     * string (see https://docs.wildfly.org/galleon/#_feature_pack_location).
     */
    private final String fpl;

    /**
     * This field would only contain channels parsed from the prosper-known-combinations.yaml file ("well-known feature packs"),
     * or a synthetic channel created when a manifest coordinate is given (via {@link Builder#setManifest(String)}).
     *
     * When channels are provided via {@link Builder#setChannelCoordinates(String)}, the channels would come in a form of
     * ChannelCoordinate(s).
     */
    private List<Channel> channels = new ArrayList<>();

    /**
     * Coordinates of channels to use for artifact resolution. Coordinate can be either GA(V) or URL.
     */
    private final List<ChannelCoordinate> channelCoordinates = new ArrayList<>();

    /**
     * List of Galleon packages to include (see https://docs.wildfly.org/galleon/#_feature_pack_archive_structure).
     */
    private final Set<String> includedPackages = new HashSet<>();

    /**
     * Maven repositories to download artifacts from. If set, this list is going to override default repositories defined in the
     * channels.
     */
    private final List<Repository> overrideRepositories = new ArrayList<>();

    /**
     * Galleon provisioning.xml file URI. Alternative to {@link #fpl}. Galleon provisioning.xml file can be used to provide
     * detailed provisioning configuration.
     */
    private final URI definition;

    private ProvisioningDefinition(Builder builder) throws NoChannelException {
        final Optional<String> fpl = Optional.ofNullable(builder.fpl);
        final Optional<URI> definition = Optional.ofNullable(builder.definitionFile);
        final Optional<ChannelManifestCoordinate> manifest = Optional.ofNullable(builder.manifest);
        final Optional<Set<String>> includedPackages = Optional.ofNullable(builder.includedPackages);

        this.includedPackages.addAll(includedPackages.orElse(Collections.emptySet()));
        this.overrideRepositories.addAll(builder.overrideRepositories);
        this.channelCoordinates.addAll(builder.channelCoordinates);

        if (fpl.isPresent() && KnownFeaturePacks.isWellKnownName(fpl.get())) {
            KnownFeaturePack featurePackInfo = KnownFeaturePacks.getByName(fpl.get());
            this.fpl = null;
            this.definition = featurePackInfo.getGalleonConfiguration();
            if (this.channelCoordinates.isEmpty()) {
                this.channels = getKnownFeaturePackChannels(overrideRepositories, manifest, featurePackInfo.getChannels());
            }
        } else if (!this.channelCoordinates.isEmpty()) {
            this.fpl = fpl.orElse(null);
            this.definition = definition.orElse(null);
        } else {
            throw Messages.MESSAGES.incompleteProvisioningConfiguration(String.join(", ", KnownFeaturePacks.getNames()));
        }

        // TODO: Do this check after channels are resolved?
        if ((channels.isEmpty() || channelsMissingManifest()) && channelCoordinates.isEmpty() && manifest.isEmpty()) {
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

    private List<RemoteRepository> channelResolutionRepositories() {
        if (overrideRepositories.isEmpty()) {
            return List.of(new RemoteRepository.Builder("MRRC", null, "https://maven.repository.redhat.com/ga/").build(),
                    new RemoteRepository.Builder("central", null, "https://repo1.maven.org/maven2").build());
        } else {
            return overrideRepositories.stream()
                    .map(RepositoryUtils::toRemoteRepository)
                    .collect(Collectors.toList());
        }
    }

    private static List<Repository> extractRepositoriesFromChannels(List<Channel> channels) {
        return channels.stream().flatMap(c -> c.getRepositories().stream()).collect(Collectors.toList());
    }

    private static List<Channel> getKnownFeaturePackChannels(List<Repository> overrideRepositories,
            Optional<ChannelManifestCoordinate> manifestRef, List<Channel> channelsFromKnownFP) {
        if (manifestRef.isPresent()) {
            final Channel channel = new Channel("", "", null, null, extractRepositoriesFromChannels(channelsFromKnownFP),
                    manifestRef.get());
            return TemporaryRepositoriesHandler.overrideRepositories(List.of(channel), overrideRepositories);
        } else {
            return TemporaryRepositoriesHandler.overrideRepositories(channelsFromKnownFP, overrideRepositories);
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

    public List<Repository> getOverrideRepositories() {
        return overrideRepositories;
    }

    public ProvisioningConfig toProvisioningConfig() throws MetadataException, ProvisioningException {
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
                throw Messages.MESSAGES.unableToParseConfigurationUri(definition, e);
            }
        } else {
            throw Messages.MESSAGES.incompleteProvisioningConfiguration(String.join(", ", KnownFeaturePacks.getNames()));
        }
    }

    /**
     * Resolves channel coordinates into Channel instances.
     *
     * @param versionResolverFactory a VersionResolverFactory instance to perform the channel resolution
     * @return Channel instances
     */
    public List<Channel> resolveChannels(VersionResolverFactory versionResolverFactory) {
        try {
            List<Channel> channels = new ArrayList<>(this.channels);

            if (!channelCoordinates.isEmpty()) {
                channels.addAll(versionResolverFactory.resolveChannels(channelCoordinates, channelResolutionRepositories()));
            }

            if (!overrideRepositories.isEmpty()) {
                channels = TemporaryRepositoriesHandler.overrideRepositories(channels, overrideRepositories);
            }

            return channels;
        } catch (MalformedURLException e) {
            // I believe the MalformedURLException is declared mistakenly by VersionResolverFactory#resolveChannels().
            throw new IllegalArgumentException(e);
        }
    }

    public static class Builder {
        private String fpl;
        private URI definitionFile;
        private List<Repository> overrideRepositories = Collections.emptyList();
        private Set<String> includedPackages;
        private ChannelManifestCoordinate manifest;
        private List<ChannelCoordinate> channelCoordinates = Collections.emptyList();

        public ProvisioningDefinition build() throws MetadataException, NoChannelException {
            return new ProvisioningDefinition(this);
        }

        public Builder setFpl(String fpl) {
            this.fpl = fpl;
            return this;
        }

        public Builder setOverrideRepositories(List<Repository> repositories) {
            this.overrideRepositories = repositories;
            return this;
        }

        public Builder setIncludedPackages(Set<String> includedPackages) {
            this.includedPackages = includedPackages;
            return this;
        }

        public Builder setManifest(String manifest) {
            if (manifest != null) {
                this.manifest = ArtifactUtils.manifestCoordFromString(manifest);
            }
            return this;
        }

        public Builder setChannelCoordinates(String channelCoordinate) {
            return setChannelCoordinates(List.of(channelCoordinate));
        }

        public Builder setChannelCoordinates(List<String> channelCoordinates) {
            Objects.requireNonNull(channelCoordinates);
            this.channelCoordinates = new ArrayList<>();
            for (String coord: channelCoordinates) {
                this.channelCoordinates.add(ArtifactUtils.channelCoordFromString(coord));
            }
            return this;
        }

        public Builder setDefinitionFile(URI provisionDefinition) {
            this.definitionFile = provisionDefinition;
            return this;
        }
    }
}
