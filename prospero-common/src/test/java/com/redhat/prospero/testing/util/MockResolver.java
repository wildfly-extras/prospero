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

package com.redhat.prospero.testing.util;

import com.redhat.prospero.api.Resolver;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class MockResolver implements Resolver, Closeable {
    private Map<String, List<String>> versions = new HashMap<>();
    private VersionScheme versionScheme = new GenericVersionScheme();
    private Path tempDir;
    private File mockArtifactFile;

    public MockResolver() throws IOException {
        tempDir = Files.createTempDirectory("artifacts");
    }

    @Override
    public void close() throws IOException {
        if (Files.exists(tempDir)) {
            Files.delete(tempDir);
        }
        if (mockArtifactFile != null && mockArtifactFile.exists()) {
            mockArtifactFile.delete();
        }
    }

    public void setArtifactRange(String ga, List<String> versions) {
        this.versions.put(ga, versions);
    }

    @Override
    public ArtifactResult resolve(Artifact artifact) throws ArtifactResolutionException {
        String ga = artifact.getGroupId()+":"+artifact.getArtifactId();
        if (versions.containsKey(ga) && versions.get(ga).contains(artifact.getVersion())) {
            final ArtifactResult artifactResult = new ArtifactResult(new ArtifactRequest());
            // mock tmp file
            try {
                mockArtifactFile = Files.createTempFile(tempDir, "artifact", "jar").toFile();
                mockArtifactFile.deleteOnExit();
                artifact = artifact.setFile(mockArtifactFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            artifactResult.setArtifact(artifact);
            return artifactResult;
        } else {
            throw new ArtifactResolutionException(Collections.emptyList());
        }
    }

    @Override
    public VersionRangeResult getVersionRange(Artifact artifact) throws VersionRangeResolutionException {
        String ga = artifact.getGroupId()+":"+artifact.getArtifactId();
        if (versions.containsKey(ga)) {
            final VersionRangeResult res = new VersionRangeResult(new VersionRangeRequest(artifact, null, null));
            return res.setVersions(versions.get(ga).stream().map(v-> toVersion(v)).collect(Collectors.toList()));
        }
        return null;
    }

    private Version toVersion(String v) {
        try {
            return versionScheme.parseVersion(v);
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }
}
