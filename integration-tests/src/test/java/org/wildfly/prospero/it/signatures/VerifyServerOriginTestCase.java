package org.wildfly.prospero.it.signatures;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.ProvisioningException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Stream;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.prospero.actions.CertificateAction;
import org.wildfly.prospero.actions.VerificationResult;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.ProvisioningProgressEvent;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.ArtifactCache;
import org.wildfly.prospero.signatures.PGPKeyId;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import org.wildfly.prospero.test.BuildProperties;
import org.wildfly.prospero.test.CertificateUtils;
import org.wildfly.prospero.test.TestInstallation;
import org.wildfly.prospero.test.TestLocalRepository;

public class VerifyServerOriginTestCase {

    protected static final String COMMONS_IO_VERSION = BuildProperties.getProperty("version.commons-io");
    protected static final String GALLEON_PLUGINS_VERSION = BuildProperties.getProperty("version.org.wildfly.galleon-plugins");
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    @ClassRule
    public static TemporaryFolder classTemp = new TemporaryFolder();
    private TestLocalRepository testLocalRepository;
    private TestInstallation testInstallation;
    private static Path serverPath;
    private static PGPSecretKeyRing pgpValidKeys;
    private static File certFile;
    private static Channel testChannel;
    private static PGPSecretKeyRing pgpInValidKeys;
    private static File baseServer;
    private static File repoPath;
    private static File baseRepoPath;

    @BeforeClass
    public static void classSetUp() throws Exception {
        baseRepoPath = classTemp.newFolder("base-local-repo");
        repoPath = classTemp.newFolder("local-repo");
        pgpValidKeys = CertificateUtils.generatePrivateKey();
        pgpInValidKeys = CertificateUtils.generatePrivateKey();
        certFile = CertificateUtils.exportPublicCertificate(pgpValidKeys, classTemp.newFile("public.crt"));

        final TestLocalRepository localRepository = new TestLocalRepository(baseRepoPath.toPath(),
                List.of(new URL("https://repo1.maven.org/maven2")));

        prepareRequiredArtifacts(localRepository);

        localRepository.deploy(TestInstallation.fpBuilder("org.test:pack-one:1.0.0")
                .addModule("commons-io", "commons-io", COMMONS_IO_VERSION)
                .addFile("test.txt", "Text 1.0.0")
                .build());
        localRepository.signAllArtifacts(pgpValidKeys);

        FileUtils.copyDirectory(baseRepoPath, repoPath);

        baseServer = classTemp.newFolder("base-server");
        final TestInstallation testInstallation = new TestInstallation(baseServer.toPath());
        testChannel = new Channel.Builder()
                .setName("test-channel")
                .setGpgCheck(true)
                .addGpgUrl(certFile.toURI().toString())
                .addRepository("local-repo", repoPath.toURI().toString())
                .setManifestCoordinate(new ChannelManifestCoordinate("org.test", "test-channel"))
                .build();


        // install server
        testInstallation.install("org.test:pack-one:1.0.0", List.of(testChannel));
    }

    @Before
    public void setUp() throws Exception {
        serverPath = temp.newFolder("test-server").toPath();
        FileUtils.copyDirectory(baseServer, serverPath.toFile());
        testInstallation = new TestInstallation(serverPath);

        FileUtils.copyDirectory(baseRepoPath, repoPath);
        testLocalRepository = new TestLocalRepository(repoPath.toPath(),
                List.of(new URL("https://repo1.maven.org/maven2")));
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteQuietly(repoPath);
    }

    @Test
    public void unsignedJarInTheServer_CausesError() throws Exception {
        // remove one of the signatures
        testLocalRepository.removeSignature("commons-io", "commons-io", COMMONS_IO_VERSION);

        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getUnsignedBinary())
                .containsExactlyInAnyOrder(new VerificationResult.InvalidBinary(
                        serverPath.relativize(testInstallation.getModulePath("commons-io", "commons-io").resolve("commons-io-" + COMMONS_IO_VERSION + ".jar")),
                        "commons-io:commons-io:jar:" + COMMONS_IO_VERSION, SignatureResult.Result.NO_SIGNATURE
                ));
        Assertions.assertThat(verify.getModifiedFiles()).isEmpty();
    }

    @Test
    public void multipleUnsignedBinariesAreReported() throws Exception {
        // remove both of the signatures
        testLocalRepository.removeSignature("commons-io", "commons-io", COMMONS_IO_VERSION);
        testLocalRepository.removeSignature("org.wildfly.galleon-plugins", "wildfly-config-gen", GALLEON_PLUGINS_VERSION);

        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getUnsignedBinary())
                .containsExactlyInAnyOrder(
                        new VerificationResult.InvalidBinary(
                                serverPath.relativize(testInstallation.getModulePath("commons-io", "commons-io")
                                        .resolve("commons-io-" + COMMONS_IO_VERSION + ".jar")),
                                "commons-io:commons-io:jar:" + COMMONS_IO_VERSION, SignatureResult.Result.NO_SIGNATURE),
                        new VerificationResult.InvalidBinary(
                                ArtifactCache.CACHE_FOLDER.resolve("wildfly-config-gen-" + GALLEON_PLUGINS_VERSION + ".jar"),
                                "org.wildfly.galleon-plugins:wildfly-config-gen:jar:" + GALLEON_PLUGINS_VERSION, SignatureResult.Result.NO_SIGNATURE)
                );
        Assertions.assertThat(verify.getModifiedFiles()).isEmpty();
    }

    @Test
    public void ignoreUpdatesWhenCheckingOrigin() throws Exception {
        // remove one of the signatures
        testLocalRepository.deployMockUpdate("commons-io", "commons-io", COMMONS_IO_VERSION, ".SP1");
        testLocalRepository.removeSignature("commons-io", "commons-io", COMMONS_IO_VERSION);

        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getUnsignedBinary())
                .containsExactlyInAnyOrder(
                        new VerificationResult.InvalidBinary(
                                serverPath.relativize(testInstallation.getModulePath("commons-io", "commons-io")
                                        .resolve("commons-io-" + COMMONS_IO_VERSION + ".jar")),
                                "commons-io:commons-io:jar:" + COMMONS_IO_VERSION, SignatureResult.Result.NO_SIGNATURE)
                );
        Assertions.assertThat(verify.getModifiedFiles()).isEmpty();
    }

    @Test
    public void invalidSignatureInTheServer_CausesError() throws Exception {
        // regenerate signature with untrusted key
        testLocalRepository.removeSignature("commons-io", "commons-io", COMMONS_IO_VERSION);
        testLocalRepository.signAllArtifacts(pgpInValidKeys);

        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getUnsignedBinary())
                .containsExactlyInAnyOrder(new VerificationResult.InvalidBinary(
                        serverPath.relativize(testInstallation.getModulePath("commons-io", "commons-io").resolve("commons-io-" + COMMONS_IO_VERSION + ".jar")),
                        "commons-io:commons-io:jar:" + COMMONS_IO_VERSION, SignatureResult.Result.NO_MATCHING_CERT,
                        new PGPKeyId(pgpInValidKeys.getPublicKey().getKeyID()).getHexKeyID()
                ));
        Assertions.assertThat(verify.getModifiedFiles()).isEmpty();
    }

    @Test
    public void corruptedJarInTheServer_CausesError() throws Exception {
        final Path moduleJarPath = testInstallation.getModulePath("commons-io", "commons-io").resolve("commons-io-" + COMMONS_IO_VERSION + ".jar");
        Files.writeString(moduleJarPath, "I'm corrupted");

        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getUnsignedBinary())
                .containsExactlyInAnyOrder(new VerificationResult.InvalidBinary(
                        serverPath.relativize(testInstallation.getModulePath("commons-io", "commons-io").resolve("commons-io-" + COMMONS_IO_VERSION + ".jar")),
                        "commons-io:commons-io:jar:" + COMMONS_IO_VERSION, SignatureResult.Result.INVALID,
                        new PGPKeyId(pgpValidKeys.getPublicKey().getKeyID()).getHexKeyID()
                ));
        Assertions.assertThat(verify.getModifiedFiles()).isEmpty();
    }

    @Test
    public void serverWithoutCacheCanBeValidated() throws Exception {
        FileUtils.deleteQuietly(serverPath.resolve(ArtifactCache.CACHE_FOLDER).toFile());

        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getUnsignedBinary())
                .isEmpty();
        Assertions.assertThat(verify.getModifiedFiles()).isEmpty();
    }

    @Test
    public void corruptedJarInTheServerCache_CausesError() throws Exception {
        final Path cachedFpZip = serverPath.resolve(ArtifactCache.CACHE_FOLDER).resolve("pack-one-1.0.0.zip");
        Assertions.assertThat(cachedFpZip).exists();
        Files.writeString(cachedFpZip, "I'm corrupted");

        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getUnsignedBinary())
                .containsExactlyInAnyOrder(new VerificationResult.InvalidBinary(
                        serverPath.relativize(cachedFpZip),
                        "org.test:pack-one:zip:1.0.0", SignatureResult.Result.INVALID,
                        new PGPKeyId(pgpValidKeys.getPublicKey().getKeyID()).getHexKeyID()
                ));
        Assertions.assertThat(verify.getModifiedFiles()).isEmpty();
    }

    @Test
    public void missingJarInTheServer_IsIgnored() throws Exception {
        FileUtils.deleteQuietly(testInstallation
                .getModulePath("commons-io", "commons-io").resolve("commons-io-" + COMMONS_IO_VERSION + ".jar")
                .toFile());

        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getUnsignedBinary())
                .isEmpty();
        Assertions.assertThat(verify.getModifiedFiles()).isEmpty();
    }

    @Test
    public void unexpectedJarInTheServer_CausesError() throws Exception {
        final Path addedBinary = testInstallation
                .getModulePath("commons-io", "commons-io").resolve("commons-io-" + COMMONS_IO_VERSION + "-SP1.jar");
        FileUtils.copyFile(testInstallation
                .getModulePath("commons-io", "commons-io").resolve("commons-io-" + COMMONS_IO_VERSION + ".jar")
                .toFile(),
                addedBinary
                        .toFile());

        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getUnsignedBinary())
                .containsExactlyInAnyOrder(new VerificationResult.InvalidBinary(
                        serverPath.relativize(addedBinary),
                        null, SignatureResult.Result.NO_SIGNATURE
                ));
        Assertions.assertThat(verify.getModifiedFiles()).isEmpty();
    }

    @Test
    public void changedFileInTheServer_CausesWarning() throws Exception {
        Assertions.assertThat(serverPath.resolve("test.txt")).exists();
        Files.writeString(serverPath.resolve("test.txt"), "User change");

        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getModifiedFiles())
                .containsExactlyInAnyOrder(
                        Path.of("test.txt")
                );
    }

    @Test
    public void listTrustedCertificatesUsedByTheServer() throws Exception {
        // run verification
        final VerificationResult verify = verifyServer();

        // expect error with failing jar listed
        Assertions.assertThat(verify.getTrustedCertificates())
                .containsExactlyInAnyOrder(PGPPublicKeyInfo.parse(pgpValidKeys.getPublicKey()));
    }

    private static VerificationResult verifyServer() throws ProvisioningException, OperationException {
        try (CertificateAction certificateAction = new CertificateAction(serverPath)) {
            return certificateAction.verifyServerOrigin(new NoopVerificationListener(), MavenOptions.OFFLINE_NO_CACHE);
        }
    }

    private static void prepareRequiredArtifacts(TestLocalRepository localRepository) throws Exception {

        localRepository.resolveAndDeploy(new DefaultArtifact("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", "jar", GALLEON_PLUGINS_VERSION));
        localRepository.resolveAndDeploy(new DefaultArtifact("org.wildfly.galleon-plugins", "wildfly-config-gen", "jar", GALLEON_PLUGINS_VERSION));
        localRepository.resolveAndDeploy(new DefaultArtifact("commons-io", "commons-io", "jar", COMMONS_IO_VERSION));

        localRepository.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.0"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", GALLEON_PLUGINS_VERSION),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", GALLEON_PLUGINS_VERSION),
                        new Stream("commons-io", "commons-io", COMMONS_IO_VERSION),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));
    }

    private static class NoopVerificationListener implements CertificateAction.VerificationListener {
        @Override
        public void progressUpdate(ProvisioningProgressEvent update) {

        }

        @Override
        public void provisionReferenceServerStarted() {

        }

        @Override
        public void provisionReferenceServerFinished() {

        }

        @Override
        public void validatingComponentsStarted() {

        }

        @Override
        public void validatingComponentsFinished() {

        }

        @Override
        public void checkingModifiedFilesStarted() {

        }

        @Override
        public void checkingModifiedFilesFinished() {

        }
    }
}
