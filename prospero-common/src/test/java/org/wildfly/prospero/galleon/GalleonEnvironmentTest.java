/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.pgpainless.PGPainless;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.ProvisioningProgressEvent;
import org.wildfly.prospero.api.exceptions.ChannelDefinitionException;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GalleonEnvironmentTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Mock
    private MavenSessionManager msm;

    @Mock
    private DefaultRepositorySystemSession session;

    @Mock
    private DefaultRepositorySystem system;

    @Test
    public void createEnvWithInvalidManifestThrowsException() throws Exception {
        final File manifest = temp.newFile();
        Files.writeString(manifest.toPath(), "schemaVersion: 1.0.0\n + foo: bar");
        final Channel build = new Channel.Builder()
                .setManifestUrl(manifest.toURI().toURL())
                .build();
        when(msm.newRepositorySystemSession(any())).thenReturn(session);

        assertThrows(ChannelDefinitionException.class, ()->
            GalleonEnvironment.builder(temp.newFolder().toPath(), List.of(build), msm, true).build());
    }

    @Test
    public void populateMavenCacheWithRevertManifests_EmptyManifests_DoesNothing() throws Exception {
        when(msm.newRepositorySystemSession(any())).thenReturn(session);
        when(msm.newRepositorySystem()).thenReturn(system);

        final ChannelManifest restoreManifest = new ChannelManifest("", null, null, Collections.emptyList());
        GalleonEnvironment.builder(temp.newFolder().toPath(), List.of(), msm, true)
                .setRestoreManifest(restoreManifest, null)
                .build();

        verifyNoInteractions(system);
    }

    @Test
    public void populateMavenCacheWithRevertManifests_MavenManifestsWithVersion_CallsResolve() throws Exception {
        when(msm.newRepositorySystemSession(any())).thenReturn(session);
        when(msm.newRepositorySystem()).thenReturn(system);
        final DefaultArtifact manifestArtifact = new DefaultArtifact("group", "artifact", "manifest", "yaml", "version");
        // mock resolving an artifact
        final ArtifactResult res = new ArtifactResult(new ArtifactRequest(manifestArtifact, null, null));
        res.setArtifact(manifestArtifact.setFile(new File("test.yaml")));
        when(system.resolveArtifact(any(), any())).thenReturn(res);

        final ChannelManifest restoreManifest = new ChannelManifest("", null, null, Collections.emptyList());
        final ManifestVersionRecord record = new ManifestVersionRecord();
        record.addManifest(new ManifestVersionRecord.MavenManifest(manifestArtifact.getGroupId(), manifestArtifact.getArtifactId(),
                manifestArtifact.getVersion(), "desc"));
        GalleonEnvironment.builder(temp.newFolder().toPath(), List.of(new Channel()), msm, true)
                .setRestoreManifest(restoreManifest, record)
                .build();

        verify(system).resolveArtifact(any(), argThat(req -> req.getArtifact().equals(manifestArtifact)));
    }
    @Test
    public void populateMavenCacheWithRevertManifests_MavenManifestsWithVersion_IgnoresErrors() throws Exception {
        final ArgumentCaptor<ArtifactRequest> argumentCaptor = ArgumentCaptor.forClass(ArtifactRequest.class);
        when(msm.newRepositorySystemSession(any())).thenReturn(session);
        when(msm.newRepositorySystem()).thenReturn(system);
        // mock failed resolution of one artifact and correct one of the other
        final DefaultArtifact missingArtifact = new DefaultArtifact("idont", "exist", "manifest", "yaml", "version");
        final DefaultArtifact manifestArtifact = new DefaultArtifact("group", "artifact", "manifest", "yaml", "version");
        final ArtifactResult res = new ArtifactResult(new ArtifactRequest(manifestArtifact, null, null));
        res.setArtifact(manifestArtifact.setFile(new File("test.yaml")));
        when(system.resolveArtifact(any(), argumentCaptor.capture()))
                .then((Answer<ArtifactResult>) inv -> {
                    final ArtifactRequest req = inv.getArgument(1, ArtifactRequest.class);
                    if (req.getArtifact().equals(missingArtifact)) {
                        throw new ArtifactResolutionException(List.of());
                    } else {
                        return res;
                    }
                });

        final ChannelManifest restoreManifest = new ChannelManifest("", null, null, Collections.emptyList());
        final ManifestVersionRecord record = new ManifestVersionRecord();
        record.addManifest(new ManifestVersionRecord.MavenManifest(missingArtifact.getGroupId(), missingArtifact.getArtifactId(),
                missingArtifact.getVersion(), "desc"));
        record.addManifest(new ManifestVersionRecord.MavenManifest(manifestArtifact.getGroupId(), manifestArtifact.getArtifactId(),
                manifestArtifact.getVersion(), "desc"));
        GalleonEnvironment.builder(temp.newFolder().toPath(), List.of(new Channel()), msm, true)
                .setRestoreManifest(restoreManifest, record)
                .build();

        verify(system, atLeast(2)).resolveArtifact(any(), any());
        assertThat(argumentCaptor.getAllValues())
                .map(ArtifactRequest::getArtifact)
                .containsOnly(missingArtifact, manifestArtifact);
    }

    @Test
    public void restoreManifestIsUsedInChannels() throws Exception {
        when(msm.newRepositorySystemSession(any())).thenReturn(session);
        when(msm.newRepositorySystem()).thenReturn(system);

        final Channel c1 = new Channel.Builder()
                .setManifestCoordinate("group", "artifactOne", "1.0.0")
                .build();
        final Channel c2 = new Channel.Builder()
                .setManifestCoordinate("group", "artifactTwo", "1.0.0")
                .build();
        final ChannelManifest restoreManifest = new ChannelManifest("restore manifest", null, null, Collections.emptyList());

        // pretend the channel manifests cannot be resolved - we'll fall back to the reverted state either way
        when(system.resolveArtifact(any(), any())).thenThrow(new ArtifactResolutionException(Collections.emptyList()));

        final URL manifestUrl;
        try (GalleonEnvironment env = GalleonEnvironment.builder(temp.newFolder().toPath(), List.of(c1, c2), msm, true)
                .setRestoreManifest(restoreManifest)
                .build()) {

            manifestUrl = env.getChannels().get(0).getManifestCoordinate().getUrl();
            final ChannelManifest resManifest = ChannelManifestMapper.from(manifestUrl);

            assertThat(resManifest.getName()).isEqualTo("restore manifest");

            assertThat(env.getChannels())
                    .map(Channel::getManifestCoordinate)
                    .map(ChannelManifestCoordinate::getUrl)
                    .containsOnly(manifestUrl);
        }
        assertThat(Path.of(manifestUrl.toURI()))
                .doesNotExist();
    }

    @Test
    public void channelSessionDoesntAcceptNewCertsIfNoConsolePresent() throws Exception {
        when(msm.newRepositorySystemSession(any())).thenReturn(session);
        when(msm.newRepositorySystem()).thenReturn(system);

        final File keystore = temp.newFile("keystore.gpg");
        try (TemporaryManifestSignature temporaryManifestSignature = new TemporaryManifestSignature(keystore.toPath())) {
            final Artifact manifestArtifact = mock(Artifact.class);
            final Artifact signatureArtifact = mock(Artifact.class);
            mockSignedArtifactResolution(manifestArtifact, signatureArtifact, temporaryManifestSignature);

            final File publicCertFile = temp.newFile("public_cert.crt");

            exportPublicKey(keystore, publicCertFile);

            final Channel c1 = new Channel.Builder()
                    .setManifestCoordinate("group", "artifactOne", "1.0.0")
                    .setGpgCheck(true)
                    .addGpgUrl(publicCertFile.toURI().toString())
                    .build();

            final GalleonEnvironment.Builder envBuilder = GalleonEnvironment.builder(temp.newFolder().toPath(), List.of(c1), msm, true)
                    .setKeyringLocation(temp.newFile("test.crt").toPath());

            assertThatThrownBy(() -> envBuilder.build())
                    .isInstanceOf(SignatureValidator.SignatureException.class)
                    .matches((e) -> ((SignatureValidator.SignatureException) e).getSignatureResult().getResult() == SignatureResult.Result.NO_MATCHING_CERT);
        }
    }

    @Test
    public void channelSessionAcceptNewCertsIfConsoleIsProvided() throws Exception {
        when(msm.newRepositorySystemSession(any())).thenReturn(session);
        when(msm.newRepositorySystem()).thenReturn(system);

        final File keystore = temp.newFile("keystore.gpg");
        try (TemporaryManifestSignature temporaryManifestSignature = new TemporaryManifestSignature(keystore.toPath())) {
            final Artifact manifestArtifact = mock(Artifact.class);
            final Artifact signatureArtifact = mock(Artifact.class);
            mockSignedArtifactResolution(manifestArtifact, signatureArtifact, temporaryManifestSignature);

            final File publicCertFile = temp.newFile("public_cert.crt");

            exportPublicKey(keystore, publicCertFile);

            final Channel c1 = new Channel.Builder()
                    .setManifestCoordinate("group", "artifactOne", "1.0.0")
                    .setGpgCheck(true)
                    .addGpgUrl(publicCertFile.toURI().toString())
                    .build();

            final GalleonEnvironment.Builder envBuilder = GalleonEnvironment.builder(temp.newFolder().toPath(), List.of(c1), msm, true)
                    .setConsole(new TestConsole())
                    .setKeyringLocation(temp.newFile("test.crt").toPath());

            try (GalleonEnvironment env = envBuilder.build()) {
                assertNotNull(env);
            }
        }
    }

    private void mockSignedArtifactResolution(Artifact manifestArtifact, Artifact signatureArtifact, TemporaryManifestSignature temporaryManifestSignature) throws IOException, PGPException, ArtifactResolutionException {
        final File manifestFile = temp.newFile("test.yaml");
        final File signatureFile = temp.newFile("test.yaml.asc");
        Files.writeString(manifestFile.toPath(), ChannelManifestMapper.toYaml(new ChannelManifest("", "", "", Collections.emptyList())));
        when(manifestArtifact.getFile()).thenReturn(manifestFile);
        when(signatureArtifact.getFile()).thenReturn(signatureFile);
        temporaryManifestSignature.sign(manifestFile, signatureFile);
        when(system.resolveArtifact(any(), any())).thenReturn(
                new ArtifactResult(mock(ArtifactRequest.class)).setArtifact(manifestArtifact),
                new ArtifactResult(mock(ArtifactRequest.class)).setArtifact(signatureArtifact)
        );
    }

    private static void exportPublicKey(File keystore, File publicCertFile) throws IOException {
        final PGPPublicKeyRing keyRing = PGPainless.readKeyRing().publicKeyRingCollection(new FileInputStream(keystore)).getKeyRings().next();
        try (ArmoredOutputStream outStream = new ArmoredOutputStream(new FileOutputStream(publicCertFile))) {
            keyRing.getPublicKey().encode(outStream);
        }
    }

    private static class TestConsole implements Console {
        @Override
        public void progressUpdate(ProvisioningProgressEvent update) {

        }

        @Override
        public void println(String text) {

        }

        @Override
        public boolean acceptPublicKey(String key) {
            return true;
        }
    }
}