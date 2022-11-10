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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.channel.Channel;
import org.wildfly.prospero.api.RepositoryUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

public class KnownFeaturePack {
    private final String name;
    private final List<Channel> channels;
    private final URI galleonConfiguration;

    @JsonCreator
    public KnownFeaturePack(@JsonProperty(value = "name") String name,
                            @JsonProperty(value = "channels") List<Channel> channels,
                            @JsonProperty(value = "galleonConfiguration") URI galleonConfiguration) {
        this.name = name;
        this.channels = channels;
        this.galleonConfiguration = galleonConfiguration;
    }

    public static void write(List<KnownFeaturePack> packs, File configFile) throws IOException {
        new ObjectMapper(new YAMLFactory()).writeValue(configFile, packs);
    }

    public static List<KnownFeaturePack> readConfig(URL url) throws IOException {
        final YAMLFactory yamlFactory = new YAMLFactory();
        final ObjectMapper objectMapper = new ObjectMapper(yamlFactory);
        return objectMapper.readValue(url, new TypeReference<List<KnownFeaturePack>>(){});
    }

    public String getName() {
        return name;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    @JsonIgnore
    public List<RemoteRepository> getRemoteRepositories() {
        final List<RemoteRepository> repositories = channels.stream()
                .flatMap(c -> c.getRepositories().stream())
                .map(r -> RepositoryUtils.toRemoteRepository(r.getId(), r.getUrl()))
                .collect(Collectors.toList());
        return repositories.stream().map(r-> RepositoryUtils.toRemoteRepository(r.getId(), r.getUrl())).collect(Collectors.toList());
    }

    public URI getGalleonConfiguration() {
        return galleonConfiguration;
    }

    @Override
    public String toString() {
        return "KnownFeaturePack{" +
                "name='" + name + '\'' +
                ", channels=" + channels +
                ", galleonConfiguration=" + galleonConfiguration +
                '}';
    }
}
