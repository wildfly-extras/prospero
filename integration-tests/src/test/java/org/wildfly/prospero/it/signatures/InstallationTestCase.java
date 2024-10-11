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
import static org.wildfly.prospero.test.CertificateUtils.result;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Stream;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.signatures.KeystoreManager;
import org.wildfly.prospero.signatures.PGPLocalKeystore;
import org.wildfly.prospero.test.BuildProperties;
import org.wildfly.prospero.test.CertificateUtils;
import org.wildfly.prospero.test.TestInstallation;
import org.wildfly.prospero.test.TestLocalRepository;

public class InstallationTestCase {

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

        prepareRequiredArtifacts(testLocalRepository);

        serverPath = temp.newFolder("server").toPath();
        testInstallation = new TestInstallation(serverPath);

        testLocalRepository.deploy(TestInstallation.fpBuilder("org.test:pack-one:1.0.0")
                .addModule("commons-io", "commons-io", "2.16.1")
                .build());
        pgpValidKeys = CertificateUtils.generatePrivateKey();
        certFile = CertificateUtils.exportPublicCertificate(pgpValidKeys, temp.newFile("public.crt"));
        testLocalRepository.signAllArtifacts(pgpValidKeys);
    }

    @Test
    public void acceptCertificateDuringInstall_RecordsCertificates() throws Exception {
        final Channel testChannel = new Channel.Builder()
                .setName("test-channel")
                .setGpgCheck(true)
                .addGpgUrl(certFile.toURI().toString())
                .addRepository("local-repo", testLocalRepository.getUri().toString())
                .setManifestCoordinate("org.test", "test-channel", "1.0.0")
                .build();

        testInstallation.install("org.test:pack-one:1.0.0", List.of(testChannel));

        testInstallation.verifyModuleJar("commons-io", "commons-io", "2.16.1");
        testInstallation.verifyInstallationMetadataPresent();
        CertificateUtils.assertKeystoreContains(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"), pgpValidKeys.getPublicKey().getKeyID());
    }

    @Test
    public void rejectCertificate_DoesNotInstallServer() throws Exception {
        final Channel testChannel = new Channel.Builder()
                .setName("test-channel")
                .setGpgCheck(true)
                .addGpgUrl(certFile.toURI().toString())
                .addRepository("local-repo", testLocalRepository.getUri().toString())
                .setManifestCoordinate("org.test", "test-channel", "1.0.0")
                .build();

        final Exception exception = Assertions.catchException(() ->
                testInstallation.install("org.test:pack-one:1.0.0", List.of(testChannel), new AcceptingConsole() {
                    @Override
                    public boolean acceptPublicKey(String key) {
                        return false;
                    }
                })
        );

        assertThat(exception)
                .isInstanceOf(SignatureValidator.SignatureException.class)
                .has(result((SignatureValidator.SignatureException) exception, SignatureResult.Result.NO_MATCHING_CERT));

        assertThat(serverPath).isEmptyDirectory();
    }

    @Test
    public void missingCertificate_DoesNotInstallServer() throws Exception {
        testLocalRepository.removeSignature("commons-io", "commons-io", BuildProperties.getProperty("version.commons-io"));

        final Channel testChannel = new Channel.Builder()
                .setName("test-channel")
                .setGpgCheck(true)
                .addGpgUrl(certFile.toURI().toString())
                .addRepository("local-repo", testLocalRepository.getUri().toString())
                .setManifestCoordinate("org.test", "test-channel", "1.0.0")
                .build();

        final Exception exception = Assertions.catchException(() ->
                testInstallation.install("org.test:pack-one:1.0.0", List.of(testChannel))
        );

        assertThat(exception)
                .hasCauseInstanceOf(SignatureValidator.SignatureException.class)
                .has(result((SignatureValidator.SignatureException) exception.getCause(), SignatureResult.Result.NO_SIGNATURE));

        assertThat(serverPath).isEmptyDirectory();
    }

    @Test
    public void installWithOfflineRepository_DoesNotRequireSignatures() throws Exception {
        final String commonsIoVersion = BuildProperties.getProperty("version.commons-io");

        TestLocalRepository testLocalRepositoryTwo = new TestLocalRepository(temp.newFolder("repo-two").toPath(),
                List.of(
                        new URL("https://repo1.maven.org/maven2"),
                        testLocalRepository.getUri().toURL()
                ));

        prepareRequiredArtifacts(testLocalRepositoryTwo);
        testLocalRepositoryTwo.resolveAndDeploy(new DefaultArtifact("org.test", "pack-one", "zip", "1.0.0"));

        final Channel testChannel = new Channel.Builder()
                .setName("test-channel")
                .setGpgCheck(true)
                .addGpgUrl(certFile.toURI().toString())
                .addRepository("local-repo", testLocalRepository.getUri().toString())
                .setManifestCoordinate("org.test", "test-channel", "1.0.0")
                .build();

        final AcceptingConsole rejectCert = new AcceptingConsole() {
            @Override
            public boolean acceptPublicKey(String key) {
                return false;
            }
        };
        testInstallation.install("org.test:pack-one:1.0.0", List.of(testChannel), rejectCert, List.of(testLocalRepositoryTwo.getUri().toURL()));

        testInstallation.verifyModuleJar("commons-io", "commons-io", commonsIoVersion);
        testInstallation.verifyInstallationMetadataPresent();
        CertificateUtils.assertKeystoreIsEmpty(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"));
    }

    @Test
    public void installUsingCustomKeystore_AcceptsKnownCerts() throws Exception {
        // prepare a keyring with imported certificate
        final Path keyring = temp.newFile("keystore.gpg").toPath();
        Files.delete(keyring);
        try (PGPLocalKeystore pgpLocalKeystore = KeystoreManager.keystoreFor(keyring);) {
            pgpLocalKeystore.importCertificate(List.of(pgpValidKeys.getPublicKey()));
        }

        // create a channel that requires the GPG checks but has no certificate URLs information
        final Channel testChannel = new Channel.Builder()
                .setName("test-channel")
                .setGpgCheck(true)
                .addRepository("local-repo", testLocalRepository.getUri().toString())
                .setManifestCoordinate("org.test", "test-channel", "1.0.0")
                .build();

        // create a console that will reject any new signatures
        final AcceptingConsole rejectingConsole = new AcceptingConsole() {
            @Override
            public boolean acceptPublicKey(String key) {
                return false;
            }
        };

        // finally, provision the server using keyring created at the beginning
        try (ProvisioningAction action = new ProvisioningAction(serverPath, MavenOptions.OFFLINE_NO_CACHE, keyring, rejectingConsole)) {
            action.provision(GalleonProvisioningConfig.builder()
                        .addFeaturePackDep(FeaturePackLocation.fromString("org.test:pack-one:1.0.0"))
                        .build(), List.of(testChannel));

        }

        // and verify we did install the server
        testInstallation.verifyModuleJar("commons-io", "commons-io", "2.16.1");
        testInstallation.verifyInstallationMetadataPresent();
        CertificateUtils.assertKeystoreContains(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"), pgpValidKeys.getPublicKey().getKeyID());
    }

    private void prepareRequiredArtifacts(TestLocalRepository localRepository) throws Exception {
        final String galleonPluginsVersion = BuildProperties.getProperty("version.org.wildfly.galleon-plugins");
        final String commonsIoVersion = BuildProperties.getProperty("version.commons-io");

        localRepository.resolveAndDeploy(new DefaultArtifact("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", "jar", galleonPluginsVersion));
        localRepository.resolveAndDeploy(new DefaultArtifact("org.wildfly.galleon-plugins", "wildfly-config-gen", "jar", galleonPluginsVersion));
        localRepository.resolveAndDeploy(new DefaultArtifact("commons-io", "commons-io", "jar", commonsIoVersion));

        localRepository.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.0"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", galleonPluginsVersion),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", galleonPluginsVersion),
                        new Stream("commons-io", "commons-io", commonsIoVersion),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));
    }

}
