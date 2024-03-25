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

import java.util.List;

public class InstallationChanges {

    private final List<ArtifactChange> artifactChanges;
    private final List<ChannelChange> channelChanges;
    private final List<FeatureChange> featureChanges;

    public InstallationChanges(List<ArtifactChange> artifactChanges, List<ChannelChange> channelChanges,
                               List<FeatureChange> featureChanges) {
        this.artifactChanges = artifactChanges;
        this.channelChanges = channelChanges;
        this.featureChanges = featureChanges;
    }

    public List<ArtifactChange> getArtifactChanges() {
        return artifactChanges;
    }

    public List<ChannelChange> getChannelChanges() {
        return channelChanges;
    }

    public List<FeatureChange> getFeatureChanges() {
        return featureChanges;
    }

    public boolean isEmpty() {
        return artifactChanges.isEmpty() && channelChanges.isEmpty();
    }
}
