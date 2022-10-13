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

package org.wildfly.prospero.actions;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.wildfly.channel.Channel;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;

/**
 * Metadata related actions wrapper.
 */
public class MetadataAction {

    private final Path installation;

    public MetadataAction(Path installation) {
        this.installation = installation;
    }

    // channels clash if they define the same manifest & repositories
    public void addChannel(Channel channel) throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            final ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            final List<Channel> channels = prosperoConfig.getWfChannels();
            // TODO: check for duplicates
            channels.add(channel);
            installationMetadata.updateProsperoConfig(prosperoConfig);
        }
    }

    public void removeChannel(int index) throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            final ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            final List<Channel> channels = prosperoConfig.getWfChannels();
            // TODO: check for duplicates
            channels.remove(index);
            installationMetadata.updateProsperoConfig(prosperoConfig);
        }
    }

    public List<Channel> getChannels() throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            return new ArrayList<>(installationMetadata.getProsperoConfig().getWfChannels());
        }
    }

    public Channel getChannel(int index) throws MetadataException {
        if (index < 0) {
            return null;
        }
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            final ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            final List<Channel> channels = prosperoConfig.getWfChannels();
            if (channels.size() <= index) {
                return null;
            }
            return channels.get(index);
        }
    }

    /**
     * Adds a remote maven repository to an installation.
     */
    @Deprecated
    public void addRepository(String name, URL url) throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            if (prosperoConfig.addRepository(new RepositoryRef(name, url.toString()))) {
                installationMetadata.updateProsperoConfig(prosperoConfig);
            } else {
                throw Messages.MESSAGES.repositoryExists(name, url);
            }
        }
    }

    /**
     * Removes a remote maven repository from an installation.
     */
    @Deprecated
    public void removeRepository(String id) throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            prosperoConfig.removeRepository(id);
            installationMetadata.updateProsperoConfig(prosperoConfig);
        }
    }

    /**
     * Retrieves maven remote repositories used by an installation.
     */
    @Deprecated
    public List<RepositoryRef> getRepositories() throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            return prosperoConfig.getRepositories();
        }
    }

    /**
     * Retrieves channels used by an installation.
     */
    @Deprecated
    public List<ChannelRef> getChannelRefs() throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            return prosperoConfig.getChannels();
        }
    }

    /**
     * Adds a channel to an installation.
     */
    @Deprecated
    public void addChannel(String gavOrUrl) throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            ChannelRef channelRef = ChannelRef.fromString(gavOrUrl);
            prosperoConfig.addChannel(channelRef);
            installationMetadata.updateProsperoConfig(prosperoConfig);
        }
    }

    /**
     * Removes a remote maven repository from an installation.
     */
    @Deprecated
    public void removeChannel(String gavOrUrl) throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            ChannelRef channelRef = ChannelRef.fromString(gavOrUrl);
            prosperoConfig.removeChannel(channelRef);
            installationMetadata.updateProsperoConfig(prosperoConfig);
        }
    }
}
