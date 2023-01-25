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

package org.wildfly.prospero.api;

import org.eclipse.aether.artifact.Artifact;
import org.wildfly.channel.version.VersionMatcher;

import java.util.Objects;
import java.util.Optional;

public class ArtifactChange extends Diff {
    public static ArtifactChange added(Artifact newVersion) {
        Objects.requireNonNull(newVersion);
        return new ArtifactChange(toGav(newVersion), null, newVersion.getVersion());
    }

    public static ArtifactChange removed(Artifact oldVersion) {
        Objects.requireNonNull(oldVersion);
        return new ArtifactChange(toGav(oldVersion), oldVersion.getVersion(), null);
    }

    public static ArtifactChange updated(Artifact oldVersion, Artifact newVersion) {
        Objects.requireNonNull(oldVersion);
        Objects.requireNonNull(newVersion);
        return new ArtifactChange(toGav(oldVersion), oldVersion.getVersion(), newVersion.getVersion());
    }

    private ArtifactChange(String gav, String oldVersion, String newVersion) {
        super(gav, oldVersion, newVersion);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public String getArtifactName() {
        // the name has to be present, as it's either old or new artifact's gav
        return getName().get();
    }

    public Optional<String> getOldVersion() {
        return getOldValue();
    }

    public Optional<String> getNewVersion() {
        return getNewValue();
    }

    private static String toGav(Artifact artifact) {
        final String gac;
        if (artifact.getClassifier() == null || artifact.getClassifier().isEmpty()) {
            gac = String.format("%s:%s", artifact.getGroupId(), artifact.getArtifactId());
        } else {
            gac = String.format("%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier());
        }
        return gac;
    }

    public boolean isDowngrade() {
        if (getNewValue().isPresent() && getOldValue().isPresent()) {
            return VersionMatcher.COMPARATOR.compare(getNewValue().get(), getOldValue().get()) < 0;
        } else {
            return false;
        }
    }

    public boolean isInstalled() {
        return getOldValue().isEmpty();
    }

    public boolean isRemoved() {
        return getNewValue().isEmpty();
    }

    public boolean isUpdated() {
        return getOldValue().isPresent() && getNewValue().isPresent();
    }
}
