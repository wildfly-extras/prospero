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

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.MavenArtifact;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class ProsperoMavenRepositoryListenerTest {

    protected static final String A_GROUP = "org.test";
    protected static final String AN_ARTIFACT = "artifact-one";
    protected static final String A_VERSION = "1.2.3";
    @Mock
    private RepositorySystemSession session;
    private ProsperoMavenRepositoryListener listener;
    private File testFile;

    @Before
    public void setUp() {

        listener = new ProsperoMavenRepositoryListener();
        testFile = new File("test");
    }

    @Test
    public void testRecordArtifact() throws Exception {
        listener.artifactResolved(new RepositoryEvent.Builder(session, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                        .setArtifact(resolvedArtifact(testFile))
                        .build());

        assertThat(listener.getManifestVersion(A_GROUP, AN_ARTIFACT))
                .isEqualTo(new MavenArtifact(A_GROUP, AN_ARTIFACT,
                        ChannelManifest.EXTENSION, ChannelManifest.CLASSIFIER, A_VERSION, testFile));

    }

    @Test
    public void testDontRecordArtifactWithoutFile() throws Exception {
        listener.artifactResolved(new RepositoryEvent.Builder(session, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                .setArtifact(resolvedArtifact(null))
                .build());

        assertThat(listener.getManifestVersion(A_GROUP, AN_ARTIFACT))
                .isNull();
    }

    @Test
    public void testDontRecordArtifactWhenEventDoesntHaveArtifact() throws Exception {
        listener.artifactResolved(new RepositoryEvent.Builder(session, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                .build());

        assertThat(listener.getManifestVersion(A_GROUP, AN_ARTIFACT))
                .isNull();
    }

    @Test
    public void testReturnNullIfArtifactHasntBeenResolved() throws Exception {
        listener.artifactResolved(new RepositoryEvent.Builder(session, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                .setArtifact(resolvedArtifact(testFile))
                .build());

        assertThat(listener.getManifestVersion(A_GROUP, "idont-exist"))
                .isNull();
    }

    @Test
    public void resolvingSameArtifactTwiceReplacesArtifact() throws Exception {
        final File testFileTwo = new File("test-two");

        listener.artifactResolved(new RepositoryEvent.Builder(session, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                .setArtifact(resolvedArtifact(testFile))
                .build());
        listener.artifactResolved(new RepositoryEvent.Builder(session, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                .setArtifact(resolvedArtifact(testFileTwo))
                .build());

        assertThat(listener.getManifestVersion(A_GROUP, AN_ARTIFACT))
                .isEqualTo(new MavenArtifact(A_GROUP, AN_ARTIFACT,
                        ChannelManifest.EXTENSION, ChannelManifest.CLASSIFIER, A_VERSION, testFileTwo));
    }

    private DefaultArtifact resolvedArtifact(File testFile) {
        if (testFile == null) {
            return new DefaultArtifact(A_GROUP, AN_ARTIFACT,
                    ChannelManifest.CLASSIFIER, ChannelManifest.EXTENSION, A_VERSION);
        } else {
            return new DefaultArtifact(A_GROUP, AN_ARTIFACT,
                    ChannelManifest.CLASSIFIER, ChannelManifest.EXTENSION, A_VERSION,
                    null, testFile);
        }
    }
}