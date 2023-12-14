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

package org.wildfly.prospero.wfchannel;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.Artifact;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.MavenArtifact;

import java.util.HashMap;
import java.util.Map;

/**
 * listener called every time an artifact is resolved by Maven. Keeps track of artifacts resolved by Maven
 */
class ProsperoMavenRepositoryListener extends AbstractRepositoryListener implements ResolvedArtifactsStore {

    private final Map<String, MavenArtifact> manifestVersions = new HashMap<>();

    @Override
    public MavenArtifact getManifestVersion(String groupId, String artifactId) {
        return manifestVersions.get(getKey(groupId, artifactId, ChannelManifest.CLASSIFIER, ChannelManifest.EXTENSION));
    }

    @Override
    public void artifactResolved(RepositoryEvent event) {
        final Artifact a = event.getArtifact();

        if (a == null || a.getFile() == null) {
            return;
        }

        if (a.getClassifier() != null && a.getClassifier().equals(ChannelManifest.CLASSIFIER)) {
            manifestVersions.put(getKey(a),
                    new MavenArtifact(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier(), a.getVersion(), a.getFile()));
        }
    }

    private static String getKey(Artifact a) {
        return getKey(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension());
    }

    private static String getKey(String groupId, String artifactId, String classifier, String extension) {
        return String.format("%s:%s:%s:%s", groupId, artifactId, nullable(classifier), nullable(extension));
    }

    private static String nullable(String txt) {
        if (txt == null) {
            return "";
        }
        return txt;
    }
}
