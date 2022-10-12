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

package org.wildfly.prospero.model;

import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.channel.Channel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ProsperoConfig {
    private List<ChannelRef> channels;
    private List<RepositoryRef> repositories;
    private List<Channel> wfChannels;

    public ProsperoConfig(List<ChannelRef> channels,
                          List<RepositoryRef> repositories) {
        this.channels = channels;
        this.repositories = repositories;
    }

    public ProsperoConfig(List<Channel> channels) {
        wfChannels = channels;
    }

    public List<Channel> getWfChannels() {
        return wfChannels;
    }

    public List<ChannelRef> getChannels() {
        return null;
    }

    public List<RepositoryRef> getRepositories() {
        return null;
    }

    public void addChannel(ChannelRef channelRef) {

    }

    public void removeChannel(ChannelRef channelRef) {

    }

    /**
     * Adds a repository to the config. If the repository already exists ({@code id} and {@code url} matches), it is not added.
     * Adding a repository with existing {@code id} but different {@code url} causes {@code IllegalArgumentException}
     * @param repository
     * @throws IllegalArgumentException if a repository with given id but different URL is already present
     * @return true if the repository was added, false if the repository already exists
     */
    public boolean addRepository(RepositoryRef repository) {
        return false;
    }

    /**
     * Removes a remote maven repository with given id from the config.
     *
     * @param id repository id
     * @throws IllegalArgumentException if a repository with given id is not present
     */
    public void removeRepository(String id) {

    }

    public void writeConfig(File configFile) throws IOException {

    }

    public static ProsperoConfig readConfig(Path path) throws IOException {
        return null;
    }

    public List<RemoteRepository> getRemoteRepositories() {
        return null;
    }
}
