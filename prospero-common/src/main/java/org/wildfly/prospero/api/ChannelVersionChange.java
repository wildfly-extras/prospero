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

package org.wildfly.prospero.api;

import java.util.Objects;
import java.util.Optional;

import org.wildfly.channel.version.VersionMatcher;

/**
 * Represents a change in a versioned channel. For example when a new version of channel manifest is available.
 *
 * The channel has two versions - {@code physical} - usually a Maven version and {@code logical} - declared by the manifest itself.
 */
public class ChannelVersionChange {
    private final String name;
    private final String oldLogicalVersion;
    private final String oldPhysicalVersion;

    private final String newLogicalVersion;
    private final String newPhysicalVersion;

    private ChannelVersionChange(Builder builder) {
        this.name = builder.name;
        this.oldLogicalVersion = builder.oldLogicalVersion;
        this.newLogicalVersion = builder.newLogicalVersion;
        this.oldPhysicalVersion = builder.oldPhysicalVersion;
        this.newPhysicalVersion = builder.newPhysicalVersion;
    }

    public String getName() {
        return name;
    }

    public String getOldLogicalVersion() {
        return oldLogicalVersion;
    }

    public String getOldPhysicalVersion() {
        return oldPhysicalVersion;
    }

    public String getNewLogicalVersion() {
        return newLogicalVersion;
    }

    public String getNewPhysicalVersion() {
        return newPhysicalVersion;
    }

    public String shortDescription() {
        final Optional<String> oldVersion = Optional.ofNullable(getOldDisplayVersion());
        final Optional<String> newVersion = Optional.ofNullable(getNewDisplayVersion());
        return String.format("%s: %s -> %s", name, oldVersion.orElse("[]"), newVersion.orElse("[]"));
    }

    public String getOldDisplayVersion() {
        if (showLogicalVersion()) {
            return oldLogicalVersion;
        } else {
            return oldPhysicalVersion;
        }
    }

    public String getNewDisplayVersion() {
        if (showLogicalVersion()) {
            return newLogicalVersion;
        } else {
            return newPhysicalVersion;
        }
    }

    private boolean showLogicalVersion() {
        if (oldPhysicalVersion != null && newPhysicalVersion != null) {
            return oldLogicalVersion != null && newLogicalVersion != null;
        } else if (oldPhysicalVersion != null) {
            return oldLogicalVersion != null;
        } else {
            return newLogicalVersion != null;
        }
    }

    public boolean isDowngrade() {
        if (newPhysicalVersion == null || oldPhysicalVersion == null) {
            return false;
        } else {
            return VersionMatcher.COMPARATOR.compare(newPhysicalVersion, oldPhysicalVersion) < 0;
        }
    }

    @Override
    public String toString() {
        return "ChannelVersionChange{" +
                "name='" + name + '\'' +
                ", oldLogicalVersion='" + oldLogicalVersion + '\'' +
                ", oldPhysicalVersion='" + oldPhysicalVersion + '\'' +
                ", newLogicalVersion='" + newLogicalVersion + '\'' +
                ", newPhysicalVersion='" + newPhysicalVersion + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelVersionChange that = (ChannelVersionChange) o;
        return Objects.equals(name, that.name) && Objects.equals(oldLogicalVersion, that.oldLogicalVersion) && Objects.equals(oldPhysicalVersion, that.oldPhysicalVersion) && Objects.equals(newLogicalVersion, that.newLogicalVersion) && Objects.equals(newPhysicalVersion, that.newPhysicalVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, oldLogicalVersion, oldPhysicalVersion, newLogicalVersion, newPhysicalVersion);
    }

    public static class Builder {
        private String name;
        private String oldLogicalVersion;
        private String oldPhysicalVersion;

        private String newLogicalVersion;
        private String newPhysicalVersion;

        public Builder(String name) {
            this.name = name;
        }

        public ChannelVersionChange build() {
            return new ChannelVersionChange(this);
        }

        public Builder setOldLogicalVersion(String oldLogicalVersion) {
            this.oldLogicalVersion = oldLogicalVersion;
            return this;
        }

        public Builder setOldPhysicalVersion(String oldPhysicalVersion) {
            this.oldPhysicalVersion = oldPhysicalVersion;
            return this;
        }

        public Builder setNewLogicalVersion(String newLogicalVersion) {
            this.newLogicalVersion = newLogicalVersion;
            return this;
        }

        public Builder setNewPhysicalVersion(String newPhysicalVersion) {
            this.newPhysicalVersion = newPhysicalVersion;
            return this;
        }
    }

}
