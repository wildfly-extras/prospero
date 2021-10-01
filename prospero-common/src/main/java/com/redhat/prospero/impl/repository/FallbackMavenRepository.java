/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.impl.repository;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Repository;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.jboss.galleon.universe.maven.MavenUniverseException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class FallbackMavenRepository implements Repository {
    private final static Logger log = LogManager.getLogger(FallbackMavenRepository.class);

    private final Repository primary;
    private final Repository fallback;

    public FallbackMavenRepository(Repository primary, Repository fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public File resolve(Artifact artifact) throws ArtifactNotFoundException {
        try {
            return primary.resolve(artifact);
        } catch (ArtifactNotFoundException ex) {
            log.info("FALLBACK: The artifact {}:{} not found in channel, falling back", artifact.getGroupId(), artifact.getArtifactId());
            return fallback.resolve(artifact);
        }
    }

    @Override
    public Artifact resolveLatestVersionOf(Artifact artifact) throws ArtifactNotFoundException {
        Artifact found = primary.resolveLatestVersionOf(artifact);
        if (found == null) {
            log.info("The artifact {}:{} not found in channel, falling back", artifact.getGroupId(), artifact.getArtifactId());
            found = fallback.resolveLatestVersionOf(artifact);
        }
        return found;
    }

    @Override
    public VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
        VersionRangeResult res = primary.getVersionRange(artifact);
        if (res.getHighestVersion() == null) {
            res = fallback.getVersionRange(artifact);
        }
        return res;
    }
}
