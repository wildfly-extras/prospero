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
package org.wildfly.prospero.it.signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Stream;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.prospero.actions.InstallationExportAction;
import org.wildfly.prospero.actions.InstallationRestoreAction;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.test.BuildProperties;
import org.wildfly.prospero.test.CertificateUtils;
import org.wildfly.prospero.test.TestInstallation;
import org.wildfly.prospero.test.TestLocalRepository;

public class RestoreTestCase {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private TestLocalRepository testLocalRepository;
    private TestInstallation testInstallation;
    private Path serverPath;
    private PGPSecretKeyRing pgpValidKeys;
    private File certFile;

    @Before
    public void setUp() throws Exception {
        testLocalRepository = new TestLocalRepository(temp.newFolder("local-repo").toPath(),
                List.of(new URL("https://repo1.maven.org/maven2")));

        prepareRequiredArtifacts();

        serverPath = temp.newFolder("server").toPath();
        testInstallation = new TestInstallation(serverPath);

        testLocalRepository.deploy(TestInstallation.fpBuilder("org.test:pack-one:1.0.0")
                .addModule("commons-io", "commons-io", "2.16.1")
                .build());
        pgpValidKeys = CertificateUtils.generatePrivateKey();
        testLocalRepository.signAllArtifacts(pgpValidKeys);

        certFile = CertificateUtils.exportPublicCertificate(pgpValidKeys, temp.newFile("public.crt"));
        final Channel testChannel = new Channel.Builder()
                .setName("test-channel")
                .setGpgCheck(true)
                .addGpgUrl(certFile.toURI().toString())
                .addRepository("local-repo", testLocalRepository.getUri().toString())
                .setManifestCoordinate(new ChannelManifestCoordinate("org.test", "test-channel"))
                .build();

        testInstallation.install("org.test:pack-one:1.0.0", List.of(testChannel));
    }

    @Test
    public void restoreInstallsServerIfCertificateIsAccepted() throws Exception {
        final Path exported = temp.newFile("exported.zip").toPath();
        new InstallationExportAction(serverPath).export(exported);
        final Path restored = temp.getRoot().toPath().resolve("restored");
        final InstallationRestoreAction restoreAction = new InstallationRestoreAction(restored, MavenOptions.DEFAULT_OPTIONS, new AcceptingConsole());
        restoreAction.restore(exported, Collections.emptyList());

        new TestInstallation(restored).verifyInstallationMetadataPresent();
        CertificateUtils.assertKeystoreContains(restored.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"),
                pgpValidKeys.getPublicKey().getKeyID());
    }

    @Test
    public void doNothingIfCertificateIsRejected() throws Exception {
        final Path exported = temp.newFile("exported.zip").toPath();
        new InstallationExportAction(serverPath).export(exported);
        final Path restored = temp.getRoot().toPath().resolve("restored");
        final InstallationRestoreAction restoreAction = new InstallationRestoreAction(restored, MavenOptions.DEFAULT_OPTIONS, new AcceptingConsole() {
            @Override
            public boolean acceptPublicKey(String key) {
                return false;
            }
        });

        assertThatThrownBy(() -> restoreAction.restore(exported, Collections.emptyList()))
                .isInstanceOf(SignatureValidator.SignatureException.class);

        assertThat(restored)
                .doesNotExist();
    }

    private void prepareRequiredArtifacts() throws Exception {
        final String galleonPluginsVersion = BuildProperties.getProperty("version.org.wildfly.galleon-plugins");
        final String commonsIoVersion = BuildProperties.getProperty("version.commons-io");

        testLocalRepository.resolveAndDeploy(new DefaultArtifact("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", "jar", galleonPluginsVersion));
        testLocalRepository.resolveAndDeploy(new DefaultArtifact("org.wildfly.galleon-plugins", "wildfly-config-gen", "jar", galleonPluginsVersion));
        testLocalRepository.resolveAndDeploy(new DefaultArtifact("commons-io", "commons-io", "jar", commonsIoVersion));

        testLocalRepository.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.0"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", galleonPluginsVersion),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", galleonPluginsVersion),
                        new Stream("commons-io", "commons-io", commonsIoVersion),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));
    }
}
