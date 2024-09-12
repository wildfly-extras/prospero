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

import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
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
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.it.utils.DirectoryComparator;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.test.BuildProperties;
import org.wildfly.prospero.test.CertificateUtils;
import org.wildfly.prospero.test.TestInstallation;
import org.wildfly.prospero.test.TestLocalRepository;

public class UpdateTestCase {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private TestLocalRepository testLocalRepository;
    private TestInstallation testInstallation;
    private Path serverPath;
    private PGPSecretKeyRing pgpValidKeys;
    private File certFile;
    private String COMMONS_IO_UPDATED_VERSION;

    @Before
    public void setUp() throws Exception {
        COMMONS_IO_UPDATED_VERSION = BuildProperties.getProperty("version.commons-io") + ".SP1";
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
    public void updateWithAcceptedCert_NoPrompt() throws Exception {
        publishUpdate();

        assertThat(testInstallation.update(new AcceptingConsole() {
            @Override
            public boolean acceptPublicKey(String key) {
                return true;
            }
        }))
                .isEmpty();

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_UPDATED_VERSION);
    }

    @Test
    public void updateWithUnknownCert_NoChanges() throws Exception {
        publishUpdate();

        // remove a keyring so we need to accept it again
        Files.delete(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"));

        final Path originalServer = temp.newFolder("original-server").toPath();
        FileUtils.copyDirectory(serverPath.toFile(), originalServer.toFile());

        Throwable exception = catchThrowable(()->testInstallation.update(new AcceptingConsole() {
            @Override
            public boolean acceptPublicKey(String key) {
                return false;
            }
        }));
        assertThat(exception)
                .isInstanceOf(SignatureValidator.SignatureException.class)
                .has(result((SignatureValidator.SignatureException) exception, SignatureResult.Result.NO_MATCHING_CERT));

        DirectoryComparator.assertNoChanges(originalServer, serverPath);
    }

    @Test
    public void updateAndAcceptNewCert_CertificateRecorded() throws Exception {
        publishUpdate();

        // remove a keyring so we need to accept it again
        Files.delete(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"));

        final Path originalServer = temp.newFolder("original-server").toPath();
        FileUtils.copyDirectory(serverPath.toFile(), originalServer.toFile());

        assertThat(testInstallation.update())
                .isEmpty();

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_UPDATED_VERSION);
        CertificateUtils.assertKeystoreContains(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"), pgpValidKeys.getPublicKey().getKeyID());
    }

    @Test
    public void invalidArtifact_NoChanges() throws Exception {
        publishUpdate();

        // remove a certificate for the updated artifact
        testLocalRepository.removeSignature("commons-io", "commons-io", COMMONS_IO_UPDATED_VERSION);

        final Path originalServer = temp.newFolder("original-server").toPath();
        FileUtils.copyDirectory(serverPath.toFile(), originalServer.toFile());

        Throwable exception = catchThrowable(()->testInstallation.update());
        assertThat(exception)
                .hasCauseInstanceOf(SignatureValidator.SignatureException.class)
                .has(result((SignatureValidator.SignatureException) exception.getCause(), SignatureResult.Result.NO_SIGNATURE));

        DirectoryComparator.assertNoChanges(originalServer, serverPath);
    }

    @Test
    public void expiredCertificate_NoChanges() throws Exception {
        publishUpdate();

        final PGPSecretKeyRing expiredPrivateKey = CertificateUtils.generateExpiredPrivateKey();
        CertificateUtils.exportPublicCertificate(expiredPrivateKey, certFile);

        // remove a certificate for the updated artifact
        testLocalRepository.removeSignature("commons-io", "commons-io", COMMONS_IO_UPDATED_VERSION);
        testLocalRepository.signAllArtifacts(expiredPrivateKey); // signs only missing signatures

        CertificateUtils.waitUntilExpires(expiredPrivateKey);

        final Path originalServer = temp.newFolder("original-server").toPath();
        FileUtils.copyDirectory(serverPath.toFile(), originalServer.toFile());

        Throwable exception = catchThrowable(()->testInstallation.update());
        assertThat(exception)
                .hasCauseInstanceOf(SignatureValidator.SignatureException.class)
                .has(result((SignatureValidator.SignatureException) exception.getCause(), SignatureResult.Result.EXPIRED));

        DirectoryComparator.assertNoChanges(originalServer, serverPath, Path.of(ProsperoMetadataUtils.METADATA_DIR, "keyring.gpg"));
    }

    @Test
    public void revokedCertificate_NoChanges() throws Exception {
        publishUpdate();
        CertificateUtils.generateRevokedKey(pgpValidKeys, certFile);

        // remove a keyring so we need to accept it again
        Files.delete(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg"));

        final Path originalServer = temp.newFolder("original-server").toPath();
        FileUtils.copyDirectory(serverPath.toFile(), originalServer.toFile());

        Throwable exception = catchThrowable(()->testInstallation.update());
        assertThat(exception)
                .isInstanceOf(SignatureValidator.SignatureException.class)
                .has(result((SignatureValidator.SignatureException) exception, SignatureResult.Result.REVOKED));

        DirectoryComparator.assertNoChanges(originalServer, serverPath, Path.of(ProsperoMetadataUtils.METADATA_DIR, "keyring.gpg"));
    }

    @Test
    public void updateWithOfflineRepository_DoesNotRequireSignatures() throws Exception {
        // remove a keyring so we there are no accepted signatures
        final Path keystore = serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg");
        Files.delete(keystore);

        final String galleonPluginsVersion = BuildProperties.getProperty("version.org.wildfly.galleon-plugins");
        final String commonsIoVersion = BuildProperties.getProperty("version.commons-io");

        TestLocalRepository testLocalRepositoryTwo = new TestLocalRepository(temp.newFolder("repo-two").toPath(),
                List.of(new URL("https://repo1.maven.org/maven2")));
        testLocalRepositoryTwo.deployMockUpdate("commons-io", "commons-io", commonsIoVersion, ".SP1");

        testLocalRepositoryTwo.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.1"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", galleonPluginsVersion),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", galleonPluginsVersion),
                        new Stream("commons-io", "commons-io", COMMONS_IO_UPDATED_VERSION),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));

        final Path originalServer = temp.newFolder("original-server").toPath();
        FileUtils.copyDirectory(serverPath.toFile(), originalServer.toFile());

        try (UpdateAction updateAction = new UpdateAction(serverPath, MavenOptions.OFFLINE_NO_CACHE, new AcceptingConsole() {
            @Override
            public boolean acceptPublicKey(String key) {
                return false;
            }
        },
                List.of(new Repository("test-repo", testLocalRepositoryTwo.getUri().toString())))) {
            updateAction.performUpdate();
        }

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_UPDATED_VERSION);
        DirectoryComparator.assertNoChanges(originalServer, serverPath,
                Path.of(ProsperoMetadataUtils.METADATA_DIR, "keyring.gpg"),
                Path.of(".galleon"),
                Path.of(ProsperoMetadataUtils.METADATA_DIR, ProsperoMetadataUtils.MANIFEST_FILE_NAME),
                Path.of(ProsperoMetadataUtils.METADATA_DIR, ProsperoMetadataUtils.CURRENT_VERSION_FILE),
                Path.of(ProsperoMetadataUtils.METADATA_DIR, ".git"),
                Path.of(ProsperoMetadataUtils.METADATA_DIR, ".cache"),
                Path.of("modules", "commons-io")
        );
        if (Files.exists(keystore)) {
            CertificateUtils.assertKeystoreIsEmpty(keystore);
        }
    }

    @Test
    public void updateWithPartialRepository() throws Exception {
        final String galleonPluginsVersion = BuildProperties.getProperty("version.org.wildfly.galleon-plugins");
        final String commonsIoVersion = BuildProperties.getProperty("version.commons-io");

        TestLocalRepository testLocalRepositoryTwo = new TestLocalRepository(temp.newFolder("repo-two").toPath(),
                List.of(new URL("https://repo1.maven.org/maven2")));
        testLocalRepositoryTwo.deployMockUpdate("commons-io", "commons-io", commonsIoVersion, ".SP1");

        testLocalRepositoryTwo.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.1"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", galleonPluginsVersion),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", galleonPluginsVersion),
                        new Stream("commons-io", "commons-io", COMMONS_IO_UPDATED_VERSION),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));
        testLocalRepositoryTwo.signAllArtifacts(pgpValidKeys);

        try (UpdateAction updateAction = new UpdateAction(serverPath, MavenOptions.OFFLINE_NO_CACHE, new AcceptingConsole(),
                List.of(new Repository("test-repo", testLocalRepositoryTwo.getUri().toString())))) {
            assertThat(updateAction.performUpdate())
                    .isEmpty();
        }

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_UPDATED_VERSION);
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
                        new Stream("commons-io", "commons-io", COMMONS_IO_UPDATED_VERSION),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));
        testLocalRepository.signAllArtifacts(pgpValidKeys);
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
