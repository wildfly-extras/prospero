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

package com.redhat.prospero.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.redhat.prospero.api.exceptions.ArtifactResolutionException;

public class ChannelRef {

    public static void writeChannels(List<ChannelRef> channelRefs, File channelsFile) throws IOException {
        new ObjectMapper(new YAMLFactory()).writeValue(channelsFile, channelRefs);
    }

    public static List<ChannelRef> readChannels(Path path) throws IOException, ArtifactResolutionException {
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, ChannelRef.class);
        final List<ChannelRef> channelRefs = objectMapper.readValue(path.toUri().toURL(), type);

        return channelRefs;
    }

    private String name;

    private String url;

    private String repoUrl;

    private String gav;

    public ChannelRef() {

    }

    public ChannelRef(String name, String repoUrl, String gav, String fileUrl) throws ArtifactResolutionException {
        this.name = name;
        this.repoUrl = repoUrl;
        this.gav = gav;
        this.url = fileUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getGav() {
        return gav;
    }

    public void setGav(String gav) {
        this.gav = gav;
    }

    @Override
    public String toString() {
        return "Channel{" + "name='" + name + '\'' + ", url='" + url + '\'' + '}';
    }
}
