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

package org.wildfly.prospero.updates;

import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.ChannelVersionChange;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UpdateSet {

    public static final UpdateSet EMPTY = new UpdateSet(Collections.emptyList());
    private final List<ArtifactChange> artifactUpdates;
    private final List<ChannelVersionChange> manifestChanges;
    private final boolean authoritativeManifestVersions;

    @Deprecated
    public UpdateSet(List<ArtifactChange> updates) {
        this(updates, Collections.emptyList(), false);
    }

    @Deprecated
    public UpdateSet(List<ArtifactChange> updates, List<ChannelVersionChange> manifestChanges) {
        this(updates, manifestChanges, false);
    }

    public UpdateSet(List<ArtifactChange> updates, List<ChannelVersionChange> manifestChanges, boolean authoritativeManifestVersions) {
        this.artifactUpdates = updates;
        this.manifestChanges = manifestChanges;
        this.authoritativeManifestVersions = authoritativeManifestVersions;
    }

    public boolean hasManifestDowngrade() {
        return getManifestChanges().stream().anyMatch(ChannelVersionChange::isDowngrade);
    }

    public List<ArtifactChange> getArtifactUpdates() {
        return artifactUpdates;
    }

    public List<ChannelVersionChange> getManifestChanges() {
        return manifestChanges;
    }

    public boolean isEmpty() {
        return artifactUpdates.isEmpty();
    }

    /**
     * set to true only if all the component changes can be identified based on manifest versions.
     * For example this is not a case if manifest uses versionPatterns
     *
     * @return
     */
    public boolean isAuthoritativeManifestVersions() {
        return authoritativeManifestVersions;
    }

    public List<String> getManifestDowngradeDescriptions() {
        final List<String> downgrades = getManifestChanges().stream()
                .filter(ChannelVersionChange::isDowngrade)
                .map(ChannelVersionChange::shortDescription)
                .collect(Collectors.toList());
        return downgrades;
    }
}
