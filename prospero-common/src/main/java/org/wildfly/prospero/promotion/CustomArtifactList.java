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

package org.wildfly.prospero.promotion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.wildfly.channel.ArtifactCoordinate;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class CustomArtifactList {

    private List<CustomArtifact> artifacts;

    @JsonCreator
    public CustomArtifactList(@JsonProperty(value = "artifacts") List<CustomArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    public List<CustomArtifact> getArtifacts() {
        return artifacts;
    }

    @JsonIgnore
    public List<ArtifactCoordinate> getArtifactCoordinates() {
        return artifacts.stream().map(CustomArtifact::toCoordinate).collect(Collectors.toList());
    }

    public static CustomArtifactList readFrom(Path path) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        return objectMapper.readValue(path.toUri().toURL(), CustomArtifactList.class);
    }

    @JsonIgnore
    public void writeTo(Path path) throws IOException {
        new ObjectMapper(new YAMLFactory()).writeValue(path.toFile(), this);
    }

    @JsonIgnore
    public String writeToString() throws IOException {
        final StringWriter stringWriter = new StringWriter();
        new ObjectMapper(new YAMLFactory()).writeValue(stringWriter, this);
        return stringWriter.toString();
    }
}
