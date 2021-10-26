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
import com.redhat.prospero.testing.util.MockResolver;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.VersionScheme;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RestoringMavenRepositoryTest {

    private MockResolver mockResolver;
    private final VersionScheme versionScheme = new GenericVersionScheme();

    @Before
    public void setup() throws Exception {
        this.mockResolver = new MockResolver();
    }

    @Test
    public void resolveLatestVersion_returnsVersionFromManifest() throws Exception {
        final DefaultArtifact expected = new DefaultArtifact("foo:bar:1.2.3.Final");
        List<Artifact> artifacts = Arrays.asList(expected);
        final RestoringMavenRepository repo = new RestoringMavenRepository(mockResolver, new Manifest(artifacts, null));
        mockResolver.setArtifactRange("foo:bar", Arrays.asList("1.2.2.Final", "1.2.3.Final", "1.2.4.Final"));

        final Artifact resolved = repo.resolveLatestVersionOf(new DefaultArtifact("foo:bar:1.2.2.Final"));

        assertEquals(expected.getGroupId(), resolved.getGroupId());
        assertEquals(expected.getArtifactId(), resolved.getArtifactId());
        assertEquals(expected.getVersion(), resolved.getVersion());
        assertEquals(expected.getClassifier(), resolved.getClassifier());
        assertNotNull(resolved.getFile());
    }


    @Test(expected = ArtifactNotFoundException.class)
    public void resolveLatesVersion_throwsExceptionIfRequestedArtifactNotInManifest() throws Exception {
        List<Artifact> artifacts = Collections.emptyList();
        final RestoringMavenRepository repo = new RestoringMavenRepository(mockResolver, new Manifest(artifacts, null));
        mockResolver.setArtifactRange("foo:bar", Arrays.asList("1.2.2.Final", "1.2.3.Final", "1.2.4.Final"));

        repo.resolveLatestVersionOf(new DefaultArtifact("foo:bar:1.2.2.Final"));
    }

    @Test(expected = ArtifactNotFoundException.class)
    public void resolveLatestVersion_throwsExceptionIfArtifactVersionInManifestCannotBeFoundInResolver() throws Exception {
        final DefaultArtifact expected = new DefaultArtifact("foo:bar:1.2.5.Final");
        List<Artifact> artifacts = Arrays.asList(expected);
        final RestoringMavenRepository repo = new RestoringMavenRepository(mockResolver, new Manifest(artifacts, null));
        mockResolver.setArtifactRange("foo:bar", Arrays.asList("1.2.2.Final", "1.2.3.Final", "1.2.4.Final"));

        repo.resolveLatestVersionOf(new DefaultArtifact("foo:bar:1.2.2.Final"));
    }

    @Test
    public void getVersion_returnsVersionFromManifest() throws Exception {
        final DefaultArtifact expected = new DefaultArtifact("foo:bar:1.2.3.Final");
        List<Artifact> artifacts = Arrays.asList(expected);
        final RestoringMavenRepository repo = new RestoringMavenRepository(mockResolver, new Manifest(artifacts, null));
        mockResolver.setArtifactRange("foo:bar", Arrays.asList("1.2.2.Final", "1.2.3.Final", "1.2.4.Final"));

        final VersionRangeResult versionRange = repo.getVersionRange(new DefaultArtifact("foo:bar:1.2.2.Final"));

        assertEquals(Arrays.asList(versionScheme.parseVersion("1.2.3.Final")), versionRange.getVersions());
    }

    @Test(expected = ArtifactNotFoundException.class)
    public void getVersion_throwsExceptionIfArtifactVersionInManifestCannotBeFoundInResolver() throws Exception {
        final DefaultArtifact expected = new DefaultArtifact("foo:bar:1.2.5.Final");
        List<Artifact> artifacts = Arrays.asList(expected);
        final RestoringMavenRepository repo = new RestoringMavenRepository(mockResolver, new Manifest(artifacts, null));
        mockResolver.setArtifactRange("foo:bar", Arrays.asList("1.2.2.Final", "1.2.3.Final", "1.2.4.Final"));

        repo.getVersionRange(new DefaultArtifact("foo:bar:1.2.2.Final"));
    }

    @Test(expected = ArtifactNotFoundException.class)
    public void getVersion_throwsExceptionIfRequestedArtifactNotInManifest() throws Exception {
        List<Artifact> artifacts = Collections.emptyList();
        final RestoringMavenRepository repo = new RestoringMavenRepository(mockResolver, new Manifest(artifacts, null));
        mockResolver.setArtifactRange("foo:bar", Arrays.asList("1.2.2.Final", "1.2.3.Final", "1.2.4.Final"));

        repo.getVersionRange(new DefaultArtifact("foo:bar:1.2.2.Final"));
    }
}
