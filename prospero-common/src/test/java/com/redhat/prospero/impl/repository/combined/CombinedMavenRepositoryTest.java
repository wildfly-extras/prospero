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
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CombinedMavenRepositoryTest {

    private final VersionScheme versionScheme = new GenericVersionScheme();

    @Mock
    private Repository r1;
    @Mock
    private Repository r2;

    @Test(expected = ArtifactNotFoundException.class)
    public void noRepositories_resolveNoArtifact() throws Exception {
        Repository repo = new CombinedMavenRepository();

        assertEquals(null, repo.resolve(new DefaultArtifact("foo:bar:1.1.1")));
    }

    @Test
    public void singleRepository_resolvesArtifactFromFirstRepo() throws Exception {
        final File tempFile = Files.createTempFile("artifact", "jar").toFile();
        when(r1.resolve(any(Artifact.class))).thenReturn(tempFile);

        final CombinedMavenRepository repo = new CombinedMavenRepository(r1);

        assertEquals(tempFile, repo.resolve(new DefaultArtifact("foo:bar:1.1.1")));
    }

    @Test
    public void twoRepositories_resolvesArtifactFromFirstRepo() throws Exception {
        final File tempFile = Files.createTempFile("artifact", "jar").toFile();
        when(r1.resolve(any(Artifact.class))).thenReturn(tempFile);

        final CombinedMavenRepository repo = new CombinedMavenRepository(r1, r2);

        assertEquals(tempFile, repo.resolve(new DefaultArtifact("foo:bar:1.1.1")));
    }

    @Test
    public void twoRepositories_resolvesArtifactFromSecondRepo() throws Exception {
        final File tempFile = Files.createTempFile("artifact", "jar").toFile();
        when(r1.resolve(any(Artifact.class))).thenThrow(new ArtifactNotFoundException(""));
        when(r2.resolve(any(Artifact.class))).thenReturn(tempFile);

        final CombinedMavenRepository repo = new CombinedMavenRepository(r1, r2);

        assertEquals(tempFile, repo.resolve(new DefaultArtifact("foo:bar:1.1.1")));
    }

    @Test(expected = ArtifactNotFoundException.class)
    public void twoRepositories_artifactNotAvailable() throws Exception {
        final File tempFile = Files.createTempFile("artifact", "jar").toFile();
        when(r1.resolve(any(Artifact.class))).thenThrow(new ArtifactNotFoundException(""));
        when(r2.resolve(any(Artifact.class))).thenThrow(new ArtifactNotFoundException(""));

        final CombinedMavenRepository repo = new CombinedMavenRepository(r1, r2);

        repo.resolve(new DefaultArtifact("foo:bar:1.1.1"));
    }

    // latestVersion
    @Test(expected = ArtifactNotFoundException.class)
    public void latestVersion_noRepositories_resolveNoArtifact() throws Exception {
        Repository repo = new CombinedMavenRepository();

        assertEquals(null, repo.resolveLatestVersionOf(new DefaultArtifact("foo:bar:1.1.1")));
    }

    @Test
    public void latestVersion_singleRepository_resolvesArtifactFromFirstRepo() throws Exception {
        final DefaultArtifact artifact = new DefaultArtifact("foo:bar:1.1.1");
        when(r1.resolveLatestVersionOf(any(Artifact.class))).thenReturn(artifact);

        final CombinedMavenRepository repo = new CombinedMavenRepository(r1);

        assertEquals(artifact, repo.resolveLatestVersionOf(artifact));
    }

    @Test
    public void latestVersion_twoRepository_resolvesArtifactFromSecondRepoOnly() throws Exception {
        final DefaultArtifact artifact = new DefaultArtifact("foo:bar:1.1.1");
        when(r1.resolveLatestVersionOf(any(Artifact.class))).thenThrow(new ArtifactNotFoundException(""));
        when(r2.resolveLatestVersionOf(any(Artifact.class))).thenReturn(artifact);

        final CombinedMavenRepository repo = new CombinedMavenRepository(r1, r2);

        assertEquals(artifact, repo.resolveLatestVersionOf(artifact));
    }

    @Test
    public void latestVersion_twoRepository_resolvesArtifactFromBothRepoOnly() throws Exception {
        final DefaultArtifact artifact = new DefaultArtifact("foo:bar:1.1.1");
        final DefaultArtifact latestArtifact = new DefaultArtifact("foo:bar:1.1.2");
        when(r1.resolveLatestVersionOf(any(Artifact.class))).thenReturn(artifact);
        when(r2.resolveLatestVersionOf(any(Artifact.class))).thenReturn(latestArtifact);

        final CombinedMavenRepository repo = new CombinedMavenRepository(r1, r2);

        assertEquals(latestArtifact, repo.resolveLatestVersionOf(artifact));
    }

    @Test
    public void getVersionRange_combineBoth() throws Exception {
        final VersionRangeResult res1 = new VersionRangeResult(new VersionRangeRequest());
        res1.setVersions(Arrays.asList("1.1.1", "1.1.2").stream().map(v-> toVersion(v, versionScheme)).collect(Collectors.toList()));
        final VersionRangeResult res2 = new VersionRangeResult(new VersionRangeRequest());
        res2.setVersions(Arrays.asList("1.1.0", "1.1.1", "1.1.3").stream().map(v-> toVersion(v, versionScheme)).collect(Collectors.toList()));
        when (r1.getVersionRange(any(Artifact.class))).thenReturn(res1);
        when (r2.getVersionRange(any(Artifact.class))).thenReturn(res2);

        final CombinedMavenRepository repo = new CombinedMavenRepository(r1, r2);

        final VersionRangeResult res = repo.getVersionRange(new DefaultArtifact("foo:bar:1.1.1"));
        assertEquals(Arrays.asList("1.1.0", "1.1.1", "1.1.2", "1.1.3").stream().map(v-> toVersion(v, versionScheme)).collect(Collectors.toList()), res.getVersions());
        assertEquals("1.1.3", res.getHighestVersion().toString());
        assertEquals("1.1.0", res.getLowestVersion().toString());
    }

    private Version toVersion(String v, VersionScheme versionScheme) {
        try {
            return versionScheme.parseVersion(v);
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException();
        }
    }


}
