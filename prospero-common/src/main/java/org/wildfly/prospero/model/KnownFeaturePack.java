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

package org.wildfly.prospero.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

public class KnownFeaturePack {
    private String name;
    private String location;
    private String channelGav;
    private List<String> packages;
    private List<RepositoryRef> repositories;

    @JsonCreator
    public KnownFeaturePack(@JsonProperty(value = "name") String name,
                            @JsonProperty(value = "location") String location,
                            @JsonProperty(value = "channelGav") String channelGav,
                            @JsonProperty(value = "packages") List<String> packages,
                            @JsonProperty(value = "repositories") List<RepositoryRef> repositories) {
        this.name = name;
        this.location = location;
        this.channelGav = channelGav;
        this.packages = packages;
        this.repositories = repositories;
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

    public String getLocation() {
        return location;
    }

    public List<String> getPackages() {
        return packages;
    }

    public List<RepositoryRef> getRepositories() {
        return repositories;
    }

    public String getChannelGav() {
        return channelGav;
    }

    @JsonIgnore
    public List<RemoteRepository> getRemoteRepositories() {
        return repositories.stream().map(RepositoryRef::toRemoteRepository).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "SupportedFpls{" +
                "name='" + name + '\'' +
                ", location='" + location + '\'' +
                ", packages=" + packages +
                ", repositories=" + repositories +
                '}';
    }
}
