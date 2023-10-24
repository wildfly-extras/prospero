/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ManifestVersionRecord {

    protected static final String SCHEMA_100 = "1.0.0";
    protected static final String DEFAULT_SCHEMA = SCHEMA_100;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MavenManifest {

        private final String description;
        private String groupId;
        private String artifactId;
        private String version;

        @Deprecated
        public MavenManifest(String groupId,
                             String artifactId,
                             String version) {
            this(groupId, artifactId, version, null);
        }

        public MavenManifest(@JsonProperty("groupId") String groupId,
                             @JsonProperty("artifactId") String artifactId,
                             @JsonProperty("version") String version,
                             @JsonProperty(value = "description", required = false) String description) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.description = description;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getDescription() {
            return description;
        }

        @JsonIgnore
        public String getSummary() {
            return String.format("[%s:%s::%s]", groupId, artifactId, version);
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UrlManifest {

        private String url;
        private String hash;
        private String description;

        @Deprecated
        public UrlManifest(String url, String hash) {
            this.url = url;
            this.hash = hash;
            this.description = null;
        }

        public UrlManifest(@JsonProperty("url") String url, @JsonProperty("hash") String hash, @JsonProperty("description") String description) {
            this.url = url;
            this.hash = hash;
            this.description = description;
        }

        public String getUrl() {
            return url;
        }

        public String getHash() {
            return hash;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getDescription() {
            return description;
        }

        @JsonIgnore
        public String getSummary() {
            return String.format("[%s::%s]", url, hash);
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NoManifest {

        private List<String> repos;
        private String strategy;
        @JsonCreator
        public NoManifest(@JsonProperty("repos") List<String> repos, @JsonProperty("strategy") String strategy) {
            this.repos = repos;
            this.strategy = strategy;
        }

        public List<String> getRepos() {
            return repos;
        }

        public String getStrategy() {
            return strategy;
        }

        @JsonIgnore
        public String getSummary() {
            return String.format("[%s::%s]", String.join("+", repos), strategy);
        }

    }

    private String schemaVersion;
    private List<MavenManifest> mavenManifests = new ArrayList<>();

    private List<UrlManifest> urlManifests = new ArrayList<>();
    private List<NoManifest> noManifests = new ArrayList<>();

    @JsonCreator
    public ManifestVersionRecord(@JsonProperty("schemaVersion") String schemaVersion,
                                 @JsonProperty("maven") List<MavenManifest> mavenManifests,
                                 @JsonProperty("url") List<UrlManifest> urlManifests,
                                 @JsonProperty("open") List<NoManifest> noManifests) {
        this.schemaVersion = schemaVersion;
        this.mavenManifests = mavenManifests==null?Collections.emptyList():mavenManifests;
        this.urlManifests = urlManifests==null?Collections.emptyList():urlManifests;
        this.noManifests = noManifests==null?Collections.emptyList():noManifests;
    }

    public ManifestVersionRecord() {
        this.schemaVersion = DEFAULT_SCHEMA;
    }

    @JsonProperty("maven")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<MavenManifest> getMavenManifests() {
        return mavenManifests;
    }

    @JsonProperty("url")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<UrlManifest> getUrlManifests() {
        return urlManifests;
    }

    @JsonProperty("open")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<NoManifest> getOpenManifests() {
        return noManifests;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void addManifest(MavenManifest manifest) {
        mavenManifests.add(manifest);
    }

    public void addManifest(NoManifest manifest) {
        noManifests.add(manifest);
    }

    public void addManifest(UrlManifest manifest) {
        urlManifests.add(manifest);
    }

    @JsonIgnore
    public String getSummary() {
        final StringBuilder sb = new StringBuilder();
        for (MavenManifest manifest : mavenManifests) {
            sb.append(manifest.getSummary());
        }
        for (UrlManifest manifest : urlManifests) {
            sb.append(manifest.getSummary());
        }
        for (NoManifest manifest : noManifests) {
            sb.append(manifest.getSummary());
        }
        return sb.toString();
    }

    public static Optional<ManifestVersionRecord> read(Path versionsFile) throws IOException {
        if (Files.exists(versionsFile)) {
            return Optional.of(ManifestVersionRecord.fromYaml(Files.readString(versionsFile)));
        } else {
            return Optional.empty();
        }
    }

    private static ManifestVersionRecord fromYaml(String yaml) throws IOException {
        return new ObjectMapper(new YAMLFactory()).readValue(yaml, ManifestVersionRecord.class);
    }

    static String toYaml(ManifestVersionRecord manifestVersionRecord) throws IOException {
        final StringWriter stringWriter = new StringWriter();
        new ObjectMapper(new YAMLFactory()).writeValue(stringWriter, manifestVersionRecord);
        return stringWriter.toString();
    }
}
