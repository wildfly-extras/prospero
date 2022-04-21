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

package com.redhat.prospero.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ProvisioningRecord {
    private List<ChannelRef> channels;
    private List<RepositoryRef> repositories;

    @JsonCreator
    public ProvisioningRecord(@JsonProperty(value = "channels") List<ChannelRef> channels,
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

    public void writeChannels(File channelsFile) throws IOException {
        new ObjectMapper(new YAMLFactory()).writeValue(channelsFile, this);
    }

    public static ProvisioningRecord readChannels(Path path) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        return objectMapper.readValue(path.toUri().toURL(), ProvisioningRecord.class);
    }
}
