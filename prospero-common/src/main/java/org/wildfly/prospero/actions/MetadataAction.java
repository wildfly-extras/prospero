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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.wildfly.channel.Channel;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.model.ProsperoConfig;

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
            final List<Channel> channels = prosperoConfig.getChannels();
            // TODO: check for duplicates
            channels.add(channel);
            installationMetadata.updateProsperoConfig(prosperoConfig);
        }
    }

    public void removeChannel(int index) throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            final ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            final List<Channel> channels = prosperoConfig.getChannels();
            // TODO: check for duplicates
            channels.remove(index);
            installationMetadata.updateProsperoConfig(prosperoConfig);
        }
    }

    public List<Channel> getChannels() throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            return new ArrayList<>(installationMetadata.getProsperoConfig().getChannels());
        }
    }

    public Channel getChannel(int index) throws MetadataException {
        if (index < 0) {
            return null;
        }
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            final ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            final List<Channel> channels = prosperoConfig.getChannels();
            if (channels.size() <= index) {
                return null;
            }
            return channels.get(index);
        }
    }
}
