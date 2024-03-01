/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Definition of the provisioning changes a feature pack requires to be installed on top of the base server.
 */
public class FeaturePackTemplate {

    private final String artifactId;
    private final String version;
    private final String groupId;
    private final List<String> additionalPackages;
    private final List<String> transitiveDependencies;
    private final boolean supportsCustomization;
    private final boolean requiresLayers;
    private final String replacesDependency;

    @JsonCreator
    public FeaturePackTemplate(@JsonProperty("groupId") String groupId,
                               @JsonProperty("artifactId") String artifactId,
                               @JsonProperty("version") String version,
                               @JsonProperty("additional-packages") List<String> additionalPackages,
                               @JsonProperty("transitive-dependencies") List<String> transitiveDependencies,
                               @JsonProperty("replaces-dependency") String replacesDependency,
                               @JsonProperty(value = "supports-customization") Boolean supportsCustomization,
                               @JsonProperty(value = "requires-layers") boolean requiresLayers
    ) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.additionalPackages = additionalPackages == null ? Collections.emptyList() : additionalPackages;
        this.transitiveDependencies = transitiveDependencies == null ? Collections.emptyList() : transitiveDependencies;
        this.replacesDependency = replacesDependency;
        this.supportsCustomization = supportsCustomization == null || supportsCustomization;
        this.requiresLayers = requiresLayers;
    }

    private FeaturePackTemplate(Builder builder) {
        this(
                builder.groupId,
                builder.artifactId,
                builder.version,
                builder.additionalPackages,
                builder.transitiveDependencies,
                builder.replacesDependency,
                builder.supportsCustomization,
                builder.requiresLayers
        );
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

    public boolean isRequiresLayers() {
        return requiresLayers;
    }

    public boolean isSupportsCustomization() {
        return supportsCustomization;
    }

    public List<String> getAdditionalPackages() {
        return additionalPackages;
    }

    public List<String> getTransitiveDependencies() {
        return transitiveDependencies;
    }

    public String getReplacesDependency() {
        return replacesDependency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeaturePackTemplate that = (FeaturePackTemplate) o;
        return supportsCustomization == that.supportsCustomization && requiresLayers == that.requiresLayers && Objects.equals(artifactId, that.artifactId) && Objects.equals(version, that.version) && Objects.equals(groupId, that.groupId) && Objects.equals(additionalPackages, that.additionalPackages) && Objects.equals(transitiveDependencies, that.transitiveDependencies) && Objects.equals(replacesDependency, that.replacesDependency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, version, groupId, additionalPackages, transitiveDependencies, supportsCustomization, requiresLayers, replacesDependency);
    }

    @Override
    public String toString() {
        return "FeaturePackRecipeMapping{" +
                "artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                ", groupId='" + groupId + '\'' +
                ", additionalPackages=" + additionalPackages +
                ", transitiveDependencies=" + transitiveDependencies +
                ", supportsCustomization=" + supportsCustomization +
                ", requiresLayers=" + requiresLayers +
                ", replacesDependency=" + replacesDependency +
                '}';
    }

    public static class Builder {
        private final String artifactId;
        private final String version;
        private final String groupId;
        private List<String> additionalPackages;
        private List<String> transitiveDependencies;
        private boolean supportsCustomization;
        private String replacesDependency;
        private boolean requiresLayers;

        public Builder(String groupId, String artifactId, String version) {
            this.artifactId = artifactId;
            this.version = version;
            this.groupId = groupId;
        }

        public FeaturePackTemplate build() {
            return new FeaturePackTemplate(this);
        }

        public Builder addAdditionalPackage(String name) {
            if (additionalPackages == null) {
                additionalPackages = new ArrayList<>();
            }
            additionalPackages.add(name);
            return this;
        }

        public Builder addTransitiveDependency(String name) {
            if (transitiveDependencies == null) {
                transitiveDependencies = new ArrayList<>();
            }
            transitiveDependencies.add(name);
            return this;
        }

        public Builder setSupportsCustomization(boolean supportsCustomization) {
            this.supportsCustomization = supportsCustomization;
            return this;
        }

        public Builder setReplacesDependency(String replacesDependency) {
            this.replacesDependency = replacesDependency;
            return this;
        }

        public Builder setRequiresLayers(boolean requiresLayers) {
            this.requiresLayers = requiresLayers;
            return this;
        }
    }
}
