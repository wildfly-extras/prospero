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

package org.wildfly.prospero.actions;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

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

    /**
     * Adds a remote maven repository to an installation.
     */
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
    public List<RepositoryRef> getRepositories() throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            return prosperoConfig.getRepositories();
        }
    }

    /**
     * Retrieves channels used by an installation.
     */
    public List<ChannelRef> getChannels() throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            return prosperoConfig.getChannels();
        }
    }

    /**
     * Adds a channel to an installation.
     */
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
    public void removeChannel(String gavOrUrl) throws MetadataException {
        try (final InstallationMetadata installationMetadata = new InstallationMetadata(installation)) {
            ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();
            ChannelRef channelRef = ChannelRef.fromString(gavOrUrl);
            prosperoConfig.removeChannel(channelRef);
            installationMetadata.updateProsperoConfig(prosperoConfig);
        }
    }
}
