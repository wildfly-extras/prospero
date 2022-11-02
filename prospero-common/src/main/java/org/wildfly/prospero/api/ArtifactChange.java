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

import java.util.Optional;

public class ArtifactChange {
    private Artifact oldVersion;
    private Artifact newVersion;

    public ArtifactChange(Artifact oldVersion, Artifact newVersion) {
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
    }

    public String getArtifactName() {
        if (oldVersion == null) {
            return toGav(newVersion);
        } else {
            return toGav(oldVersion);
        }
    }

    public Optional<String> getOldVersion() {
        return oldVersion==null?Optional.empty():Optional.of(oldVersion.getVersion());
    }

    public Optional<String> getNewVersion() {
        return newVersion==null?Optional.empty():Optional.of(newVersion.getVersion());
    }

    @Override
    public String toString() {
        if (oldVersion == null) {
            return String.format("Install [%s]:\t\t [] ==> %s", toGav(newVersion), newVersion.getVersion());
        }
        if (newVersion == null) {
            return String.format("Remove [%s]:\t\t %s ==> []", toGav(oldVersion), oldVersion.getVersion());
        }
        final String gac = toGav(oldVersion);
        return String.format("Update [%s]:\t\t %s ==> %s", gac, oldVersion.getVersion(), newVersion.getVersion());
    }

    private String toGav(Artifact artifact) {
        final String gac;
        if (artifact.getClassifier() == null || artifact.getClassifier().isEmpty()) {
            gac = String.format("%s:%s", artifact.getGroupId(), artifact.getArtifactId());
        } else {
            gac = String.format("%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier());
        }
        return gac;
    }

    public boolean isDowngrade() {
        if (getNewVersion().isPresent() && getOldVersion().isPresent()) {
            return VersionMatcher.COMPARATOR.compare(getNewVersion().get(), getOldVersion().get()) < 0;
        } else {
            return false;
        }
    }
}
