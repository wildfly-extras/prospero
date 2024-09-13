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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.wildfly.prospero.test.CertificateUtils.result;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Stream;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.it.utils.DirectoryComparator;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.test.BuildProperties;
import org.wildfly.prospero.test.CertificateUtils;
import org.wildfly.prospero.test.TestInstallation;
import org.wildfly.prospero.test.TestLocalRepository;

public class RevertTestCase {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private TestLocalRepository testLocalRepository;
    private TestInstallation testInstallation;
    private Path serverPath;
    private PGPSecretKeyRing pgpValidKeys;
    private File certFile;
    private String COMMONS_IO_VERSION;

    @Before
    public void setUp() throws Exception {
        COMMONS_IO_VERSION = BuildProperties.getProperty("version.commons-io");
        testLocalRepository = new TestLocalRepository(temp.newFolder("local-repo").toPath(),
                List.of(new URL("https://repo1.maven.org/maven2")));

        prepareRequiredArtifacts(testLocalRepository);

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

        publishUpdate();
        testInstallation.update();
    }

    @Test
    public void revertToOriginalInstallation() throws Exception {
        testInstallation.revertToOriginalState();

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_VERSION);
        CertificateUtils.assertKeystoreContainsOnly(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"), pgpValidKeys.getPublicKey().getKeyID());
    }

    @Test
    public void revertToOriginalInstallation_RemovedKeystoreAsksForConfirmation() throws Exception {
        // remove a keyring so we need to accept it again
        Files.delete(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"));

        testInstallation.revertToOriginalState();
        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_VERSION);
        CertificateUtils.assertKeystoreContainsOnly(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"), pgpValidKeys.getPublicKey().getKeyID());
    }

    @Test
    public void revertToOriginalInstallation_RemovedKeystoreRejectedConfirmation_NoChanges() throws Exception {
        // remove a keyring so we need to accept it again
        Files.delete(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"));

        final Path originalServer = temp.newFolder("original-server").toPath();
        FileUtils.copyDirectory(serverPath.toFile(), originalServer.toFile());

        Throwable exception = catchThrowable(()-> testInstallation.revertToOriginalState(new AcceptingConsole() {
            @Override
            public boolean acceptPublicKey(String key) {
                return false;
            }
        }));
        assertThat(exception)
                .isInstanceOf(SignatureValidator.SignatureException.class)
                .has(result((SignatureValidator.SignatureException) exception, SignatureResult.Result.NO_MATCHING_CERT));

        DirectoryComparator.assertNoChanges(originalServer, serverPath,
                Path.of(ProsperoMetadataUtils.METADATA_DIR, "keyring.gpg"),
                Path.of(ProsperoMetadataUtils.METADATA_DIR, ".git", "ORIG_HEAD"));
    }

    @Test
    public void revertWithOfflineRepository_DoesNotRequireSignatures() throws Exception {
        // remove a keyring so we need to accept it again
        final Path keystoreLocation = serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg");
        Files.delete(keystoreLocation);

        TestLocalRepository testLocalRepositoryTwo = new TestLocalRepository(temp.newFolder("repo-two").toPath(),
                List.of(new URL("https://repo1.maven.org/maven2")));
        prepareRequiredArtifacts(testLocalRepositoryTwo);

        final Path originalServer = temp.newFolder("original-server").toPath();
        FileUtils.copyDirectory(serverPath.toFile(), originalServer.toFile());

        testInstallation.revertToOriginalState(new AcceptingConsole() {
            @Override
            public boolean acceptPublicKey(String key) {
                return false;
            }
        }, List.of(testLocalRepositoryTwo.getUri().toURL()));

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_VERSION);
        DirectoryComparator.assertNoChanges(originalServer, serverPath,
                Path.of(ProsperoMetadataUtils.METADATA_DIR, "keyring.gpg"),
                Path.of(".galleon"),
                Path.of(ProsperoMetadataUtils.METADATA_DIR, ProsperoMetadataUtils.MANIFEST_FILE_NAME),
                Path.of(ProsperoMetadataUtils.METADATA_DIR, ProsperoMetadataUtils.CURRENT_VERSION_FILE),
                Path.of(ProsperoMetadataUtils.METADATA_DIR, ".git"),
                Path.of(ProsperoMetadataUtils.METADATA_DIR, ".cache"),
                Path.of("modules", "commons-io")
        );
        if (Files.exists(keystoreLocation)) {
            CertificateUtils.assertKeystoreIsEmpty(keystoreLocation);
        }
    }


    private void publishUpdate() throws ArtifactResolutionException, DeploymentException, IOException {
        final String galleonPluginsVersion = BuildProperties.getProperty("version.org.wildfly.galleon-plugins");
        final String commonsIoVersion = BuildProperties.getProperty("version.commons-io");

        testLocalRepository.deployMockUpdate("commons-io", "commons-io", commonsIoVersion, ".SP1");

        testLocalRepository.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.1"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", galleonPluginsVersion),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", galleonPluginsVersion),
                        new Stream("commons-io", "commons-io", commonsIoVersion + ".SP1"),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));
        testLocalRepository.signAllArtifacts(pgpValidKeys);
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
