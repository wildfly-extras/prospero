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

package com.redhat.prospero.impl.repository.combined;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Repository;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class CombinedMavenRepository implements Repository {

    private final List<Repository> repos;
    private final VersionScheme versionScheme = new GenericVersionScheme();

    public CombinedMavenRepository(Repository... repos) {
        this(Arrays.asList(repos));
    }

    public CombinedMavenRepository(List<Repository> repos) {
        this.repos = repos;
    }

    @Override
    public File resolve(Artifact artifact) throws ArtifactNotFoundException {
        if (this.repos == null || repos.isEmpty()) {
            throw new ArtifactNotFoundException("Artifact not found " + artifact);
        }

        for (Repository repo : repos) {
            try {
                return repo.resolve(artifact);
            } catch (ArtifactNotFoundException e) {
                // OK, keep looking
            }
        }

        // haven't found it in any repository
        throw new ArtifactNotFoundException("Artifact not found " + artifact);
    }

    @Override
    public Artifact resolveLatestVersionOf(Artifact artifact) throws ArtifactNotFoundException {
        if (this.repos == null || repos.isEmpty()) {
            throw new ArtifactNotFoundException("Artifact not found " + artifact);
        }

        Artifact latest = null;
        for (Repository repo : repos) {
            try {
                Artifact candidate = repo.resolveLatestVersionOf(artifact);
                if (latest == null || versionScheme.parseVersion(candidate.getVersion()).compareTo(versionScheme.parseVersion(latest.getVersion())) > 0) {
                    latest = candidate;
                }
            } catch (ArtifactNotFoundException e) {
                // OK, keep looking
            } catch (InvalidVersionSpecificationException e) {
                e.printStackTrace();
            }
        }

        if (latest == null) {
            // haven't found it in any repository
            throw new ArtifactNotFoundException("Artifact not found " + artifact);
        }

        return latest;
    }

    @Override
    public VersionRangeResult getVersionRange(Artifact artifact) throws ArtifactNotFoundException {
        if (this.repos == null || repos.isEmpty()) {
            throw new ArtifactNotFoundException("Artifact not found " + artifact);
        }

        VersionRangeResult versionRangeResult = null;

        // use TreeSet to order the versions
        Set<Version> versions = new TreeSet<>();
        for (Repository repo : repos) {
            VersionRangeResult range = repo.getVersionRange(artifact);
            range.getVersions().forEach(versions::add);

            // save first version as response
            if (versionRangeResult == null) {
                versionRangeResult = range;
            }
        }

        versionRangeResult.setVersions(new ArrayList<>(versions));

        return versionRangeResult;
    }
}
