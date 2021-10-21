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

package com.redhat.prospero.impl.repository.restore;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.api.Resolver;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionScheme;

import java.io.File;
import java.util.Arrays;

public class RestoringMavenRepository implements Repository {

    private final Manifest manifest;
    private final Resolver resolver;
    private final VersionScheme versionScheme = new GenericVersionScheme();

    public RestoringMavenRepository(Resolver resolver, Manifest manifest) {
        this.manifest = manifest;
        this.resolver = resolver;
    }

    @Override
    public File resolve(Artifact artifact) throws ArtifactNotFoundException {
        try {
            final ArtifactResult result = resolver.resolve(artifact);

            if (!result.isResolved()) {
                throw new ArtifactNotFoundException("Failed to resolve " + artifact);
            }
            if (result.isMissing()) {
                throw new ArtifactNotFoundException("Repository is missing artifact " + artifact);
            }
            return result.getArtifact().getFile();
        } catch (ArtifactResolutionException e) {
            throw new ArtifactNotFoundException(String.format("Unable to resolve artifact %s in configured maven repositories.", artifact), e);
        }
    }

    @Override
    public Artifact resolveLatestVersionOf(Artifact artifact) throws ArtifactNotFoundException {
        final Artifact artifactInManifest = manifest.find(artifact);
        if (artifactInManifest == null) {
            throw new ArtifactNotFoundException(String.format("Artifact %s not found in provided manifest", artifact));
        }

        return artifactInManifest.setFile(resolve(artifactInManifest));
    }

    @Override
    public VersionRangeResult getVersionRange(Artifact artifact) throws ArtifactNotFoundException {
        final Artifact artifactInManifest = manifest.find(artifact);
        if (artifactInManifest == null) {
            throw new ArtifactNotFoundException(String.format("Artifact %s not found in provided manifest", artifact));
        }

        // check it exist in the repositories
        resolve(artifactInManifest);

        VersionRangeRequest req = new VersionRangeRequest();
        req.setArtifact(artifact);
        final VersionRangeResult result = new VersionRangeResult(req);
        try {
            result.setVersions(Arrays.asList(versionScheme.parseVersion(artifactInManifest.getVersion())));
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalArgumentException(e);
        }
        return result;
    }
}
