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

import org.wildfly.channel.MavenArtifact;

/**
 * a collection of artifacts resolved in the maven session
 */
public interface ResolvedArtifactsStore {

    /**
     * queries the store to find a manifest resolved in this maven session matching the {@code GA}
     *
     * @param groupId - the {@code groupId} of the manifest
     * @param artifactId - the {@code artifactId} of the manifest
     * @return - {@code MavenArtifact} representing the resolved manifest or {@code null} if the manifest has not been resolved
     */
    MavenArtifact getManifestVersion(String groupId, String artifactId);
}
