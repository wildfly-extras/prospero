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

package org.wildfly.prospero.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS;

public class MavenOptions {
    private static final YAMLFactory YAML_FACTORY = new YAMLFactory()
            .configure(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR, true);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(YAML_FACTORY)
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(ORDER_MAP_ENTRIES_BY_KEYS, true);
    private final Optional<Path> localCache;
    private final Optional<Boolean> offline;
    private final Optional<Boolean> noLocalCache;

    public static final MavenOptions DEFAULT_OPTIONS = builder().build();
    public static final MavenOptions OFFLINE_NO_CACHE = builder()
            .setOffline(true)
            .setNoLocalCache(true)
            .build();

    public static final MavenOptions OFFLINE = builder()
            .setOffline(true)
            .build();

    public static MavenOptions.Builder builder() {
        return new Builder();
    }

    @JsonCreator
    private MavenOptions(@JsonProperty("localCache") Path localCache,
                         @JsonProperty("offline") boolean offline,
                         @JsonProperty("noLocalCache") boolean noLocalCache) {
        this.localCache = Optional.ofNullable(localCache).map(Path::toAbsolutePath);
        this.noLocalCache = Optional.of(noLocalCache);
        this.offline = Optional.of(offline);
    }

    private MavenOptions(Optional<Path> localCache, Optional<Boolean> offline, Optional<Boolean> noLocalCache) {
        this.localCache = localCache;
        this.noLocalCache = noLocalCache;
        this.offline = offline;
    }

    public Path getLocalCache() {
        return localCache.orElse(null);
    }

    public boolean isOffline() {
        return offline.orElse(false);
    }

    public boolean isNoLocalCache() {
        return noLocalCache.orElse(false);
    }


    public boolean overridesLocalCache() {
        return localCache.isPresent();
    }

    @Override
    public String toString() {
        return "MavenOptions{" +
                "localCache=" + localCache +
                ", offline=" + offline +
                ", noLocalCache=" + noLocalCache +
                '}';
    }

    public MavenOptions merge(MavenOptions override) {
        final Builder builder = builder();
        if (override.offline.isPresent()) {
            builder.setOffline(override.isOffline());
        } else if (this.offline.isPresent()) {
            builder.setOffline(this.isOffline());
        }

        if (override.noLocalCache.isPresent()) {
            builder.setNoLocalCache(override.isNoLocalCache());
        } else if (this.noLocalCache.isPresent()) {
            builder.setNoLocalCache(this.isNoLocalCache());
        }

        if (override.localCache.isPresent()) {
            builder.setLocalCachePath(override.getLocalCache());
        } else if (this.localCache.isPresent()) {
            builder.setLocalCachePath(this.getLocalCache());
        }
        return builder.build();
    }

    public void write(Path target) throws IOException {
        final StringWriter w = new StringWriter();
        OBJECT_MAPPER.writeValue(w, this);
        Files.writeString(target, w.toString());
    }

    public static MavenOptions read(Path target) throws IOException {
        return OBJECT_MAPPER.readValue(target.toFile(), MavenOptions.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MavenOptions that = (MavenOptions) o;
        return Objects.equals(localCache, that.localCache) && Objects.equals(offline, that.offline) && Objects.equals(noLocalCache, that.noLocalCache);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localCache, offline, noLocalCache);
    }

    public static class Builder {

        private Optional<Boolean> offline = Optional.empty();
        private Optional<Boolean> noLocalCache = Optional.empty();
        private Optional<Path> localCachePath = Optional.empty();

        private Builder() {

        }

        public MavenOptions build() {
            return new MavenOptions(localCachePath, offline, noLocalCache);
        }

        public Builder setOffline(boolean offline) {
            this.offline = Optional.of(offline);
            return this;
        }

        public Builder setNoLocalCache(boolean noLocalCache) {
            this.noLocalCache = Optional.of(noLocalCache);
            return this;
        }

        public Builder setLocalCachePath(Path localCachePath) {
            this.localCachePath = Optional.of(localCachePath);
            return this;
        }
    }
}
