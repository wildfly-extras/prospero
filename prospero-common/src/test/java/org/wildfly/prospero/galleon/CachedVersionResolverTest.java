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

package org.wildfly.prospero.galleon;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.spi.MavenVersionsResolver;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CachedVersionResolverTest {
    @Mock
    private MavenVersionsResolver mockResolver;
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    @Mock
    private RepositorySystemSession session;
    @Mock
    private RepositorySystem system;
    @Mock
    private ArtifactCache artifactCache;
    @Captor
    private ArgumentCaptor<InstallRequest> requestCaptor;
    @Captor
    private ArgumentCaptor<List<ArtifactCoordinate>> listCaptor;

    private static final MavenArtifact ARTIFACT;

    static {
        try {
            ARTIFACT = MavenArtifact.fromString("group:artifact:jar:classifier:1.0.0");
        } catch (MavenUniverseException e) {
            throw new RuntimeException(e);
        }
    }

    private CachedVersionResolver resolver;

    @Before
    public void setUp() throws Exception {
        resolver = new CachedVersionResolver(mockResolver, artifactCache, system, session);
    }

    @Test
    public void testNoCacheFallbackToWrappedResolver() throws Exception {
        when(artifactCache.getArtifact(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        resolver.resolveArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion());

        verify(mockResolver).resolveArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion());
    }

    @Test
    public void testCacheMatchesCoordinatesResolverNotCalled() throws Exception {
        final File testJar = temp.newFile("test.jar");
        when(artifactCache.getArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion()))
                .thenReturn(Optional.of(testJar));

        File resolved = resolver.resolveArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion());

        verify(mockResolver, never()).resolveArtifact(any(), any(), any(), any(), any());
        assertEquals(testJar, resolved);
    }

    @Test
    public void testCacheMatchesArtifactInstalledLocally() throws Exception {
        final File testJar = temp.newFile("test.jar");
        when(artifactCache.getArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion()))
                .thenReturn(Optional.of(testJar));

        resolver.resolveArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion());

        verify(system).install(eq(session), requestCaptor.capture());
        final Artifact artifact = requestCaptor.getValue().getArtifacts().stream().findFirst().get();
        assertEquals(ARTIFACT.getGroupId(), artifact.getGroupId());
        assertEquals(ARTIFACT.getArtifactId(), artifact.getArtifactId());
        assertEquals(ARTIFACT.getVersion(), artifact.getVersion());
        assertEquals(ARTIFACT.getClassifier(), artifact.getClassifier());
        assertEquals(ARTIFACT.getExtension(), artifact.getExtension());
        assertEquals(testJar, artifact.getFile());
    }

    @Test
    public void testLocalInstallationFailsFailoverToResolver() throws Exception {
        final File testJar = temp.newFile("test.jar");
        final File testJar2 = temp.newFile("test2.jar");
        when(system.install(any(), any())).thenThrow(new InstallationException("test"));
        when(artifactCache.getArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion()))
                .thenReturn(Optional.of(testJar));
        when(mockResolver.resolveArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion()))
                .thenReturn(testJar2);

        File resolved = resolver.resolveArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion());

        verify(mockResolver).resolveArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion());
        assertEquals(testJar2, resolved);
    }

    @Test
    public void testBulkResolve() throws Exception {
        final File testJar = temp.newFile("test.jar");
        final File testJar2 = temp.newFile("test2.jar");
        when(artifactCache.getArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion()))
                .thenReturn(Optional.of(testJar));
        when(artifactCache.getArtifact(ARTIFACT.getGroupId(), "artifactTwo", ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion()))
                .thenReturn(Optional.empty());
        when(mockResolver.resolveArtifacts(listCaptor.capture()))
                .thenReturn(List.of(testJar2));

        final List<File> resolved = resolver.resolveArtifacts(List.of(
                new ArtifactCoordinate(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion()),
                new ArtifactCoordinate(ARTIFACT.getGroupId(), "artifactTwo", ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion())));

        assertThat(resolved)
                .containsExactly(testJar, testJar2);
        verify(artifactCache, times(2)).getArtifact(any(), any(), any(), any(), any());
        verify(mockResolver).resolveArtifacts(any());
        assertEquals("artifactTwo", listCaptor.getValue().get(0).getArtifactId());
    }

    @Test
    public void testBulkResolveFailsToInstall() throws Exception {
        final File testJar = temp.newFile("test.jar");
        final File testJar2 = temp.newFile("test2.jar");
        when(artifactCache.getArtifact(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion()))
                .thenReturn(Optional.of(testJar));
        when(system.install(any(), any())).thenThrow(new InstallationException("test"));
        when(artifactCache.getArtifact(ARTIFACT.getGroupId(), "artifactTwo", ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion()))
                .thenReturn(Optional.empty());
        when(mockResolver.resolveArtifacts(listCaptor.capture()))
                .thenReturn(List.of(testJar, testJar2));

        final List<File> resolved = resolver.resolveArtifacts(List.of(
                new ArtifactCoordinate(ARTIFACT.getGroupId(), ARTIFACT.getArtifactId(), ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion()),
                new ArtifactCoordinate(ARTIFACT.getGroupId(), "artifactTwo", ARTIFACT.getExtension(), ARTIFACT.getClassifier(), ARTIFACT.getVersion())));

        assertThat(resolved)
                .containsExactly(testJar, testJar2);
        verify(artifactCache, times(2)).getArtifact(any(), any(), any(), any(), any());
        verify(mockResolver).resolveArtifacts(any());
        assertEquals("artifact", listCaptor.getValue().get(0).getArtifactId());
        assertEquals("artifactTwo", listCaptor.getValue().get(1).getArtifactId());
    }
}