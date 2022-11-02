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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.prospero.Messages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProsperoConfig {
    private final List<ChannelRef> channels;
    private final List<RepositoryRef> repositories;

    @JsonCreator
    public ProsperoConfig(@JsonProperty(value = "channels") List<ChannelRef> channels,
                          @JsonProperty(value = "repositories") List<RepositoryRef> repositories) {
        this.channels = channels;
        this.repositories = repositories;
    }

    public List<ChannelRef> getChannels() {
        return channels;
    }

    public List<RepositoryRef> getRepositories() {
        return repositories;
    }

    public void addChannel(ChannelRef channelRef) {
        // Check that neither GAV nor URL of added channel is equal to GAVs or URLs of existing channels.
        if (StringUtils.isNotBlank(channelRef.getGav())) {
            Optional<ChannelRef> found = channels.stream().filter(c -> channelRef.getGav().equals(c.getGav())).findAny();
            if (found.isPresent()) {
                throw Messages.MESSAGES.channelExists(channelRef.getGav());
            }
        }
        if (StringUtils.isNotBlank(channelRef.getUrl())) {
            Optional<ChannelRef> found = channels.stream().filter(c -> channelRef.getUrl().equals(c.getUrl())).findAny();
            if (found.isPresent()) {
                throw Messages.MESSAGES.channelExists(channelRef.getUrl());
            }
        }
        channels.add(0, channelRef);
    }

    public void removeChannel(ChannelRef channelRef) {
        Optional<ChannelRef> found = Optional.empty();
        if (StringUtils.isNotBlank(channelRef.getGav())) {
            found = channels.stream().filter(c -> channelRef.getGav().equals(c.getGav())).findFirst();
        }
        if (found.isEmpty() && StringUtils.isNotBlank(channelRef.getUrl())) {
            found = channels.stream().filter(c -> channelRef.getUrl().equals(c.getUrl())).findFirst();
        }
        if (found.isEmpty()) {
            throw Messages.MESSAGES.channelNotPresent(channelRef.getGavOrUrlString());
        }
        channels.remove(found.get());
    }

    /**
     * Adds a repository to the config. If the repository already exists ({@code id} and {@code url} matches), it is not added.
     * Adding a repository with existing {@code id} but different {@code url} causes {@code IllegalArgumentException}
     * @param repository
     * @throws IllegalArgumentException if a repository with given id but different URL is already present
     * @return true if the repository was added, false if the repository already exists
     */
    public boolean addRepository(RepositoryRef repository) {
        final Optional<RepositoryRef> matched = repositories.stream().filter(r -> r.getId().equals(repository.getId())).findFirst();
        if (matched.isPresent())
            if (matched.get().getUrl().equals(repository.getUrl())) {
                return false;
            } else {
                throw new IllegalArgumentException(String.format("Repository %s already exists with different url", repository.getId()));
            }
        repositories.add(repository);
        return true;
    }

    /**
     * Removes a remote maven repository with given id from the config.
     *
     * @param id repository id
     * @throws IllegalArgumentException if a repository with given id is not present
     */
    public void removeRepository(String id) {
        Optional<RepositoryRef> repo = repositories.stream()
                .filter(r -> id.equals(r.getId()))
                .findFirst();
        if (repo.isPresent()) {
            repositories.remove(repo.get());
        } else {
            throw Messages.MESSAGES.repositoryNotPresent(id);
        }
    }

    public void writeConfig(File configFile) throws IOException {
        ProsperoConfig toWrite = new ProsperoConfig(this.getChannels().stream().map(ChannelRef::new).collect(Collectors.toList()), this.getRepositories());
        new ObjectMapper(new YAMLFactory()).writeValue(configFile, toWrite);
    }

    public static ProsperoConfig readConfig(Path path) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        return objectMapper.readValue(path.toFile(), ProsperoConfig.class);
    }

    @JsonIgnore
    public List<RemoteRepository> getRemoteRepositories() {
        return repositories.stream().map(RepositoryRef::toRemoteRepository).collect(Collectors.toList());
    }
}
