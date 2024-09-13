package org.wildfly.prospero.it.signatures;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.test.BuildProperties;
import org.wildfly.prospero.test.CertificateUtils;
import org.wildfly.prospero.test.TestInstallation;
import org.wildfly.prospero.test.TestLocalRepository;

public class MixedChannelTestCase {
    protected static final String COMMONS_IO_VERSION1 = BuildProperties.getProperty("version.commons-io");
    protected static final String COMMONS_CODEC_VERSION = BuildProperties.getProperty("version.commons-codec");
    protected static final String GALLEON_PLUGINS_VERSION = BuildProperties.getProperty("version.org.wildfly.galleon-plugins");
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private TestLocalRepository testLocalRepositoryOne;
    private TestLocalRepository testLocalRepositoryTwo;
    private PGPSecretKeyRing pgpValidKeys;
    private File certFile;
    private String COMMONS_IO_VERSION;
    private List<Channel> channels;
    private Path serverPath;
    private TestInstallation testInstallation;

    @Before
    public void setUp() throws Exception {
        COMMONS_IO_VERSION = BuildProperties.getProperty("version.commons-io");
        testLocalRepositoryOne = new TestLocalRepository(temp.newFolder("local-repo-one").toPath(),
                List.of(new URL("https://repo1.maven.org/maven2")));
        testLocalRepositoryTwo = new TestLocalRepository(temp.newFolder("local-repo-two").toPath(),
                List.of(new URL("https://repo1.maven.org/maven2")));

        pgpValidKeys = CertificateUtils.generatePrivateKey();
        certFile = CertificateUtils.exportPublicCertificate(pgpValidKeys, temp.newFile("public.crt"));

        testLocalRepositoryOne.resolveAndDeploy(new DefaultArtifact("org.wildfly.galleon-plugins", "wildfly-config-gen", "jar", GALLEON_PLUGINS_VERSION));
        testLocalRepositoryOne.resolveAndDeploy(new DefaultArtifact("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", "jar", GALLEON_PLUGINS_VERSION));
        testLocalRepositoryOne.resolveAndDeploy(new DefaultArtifact("commons-io", "commons-io", "jar", COMMONS_IO_VERSION1));
        testLocalRepositoryOne.deployMockUpdate("commons-io", "commons-io", COMMONS_IO_VERSION, ".SP1");

        testLocalRepositoryTwo.resolveAndDeploy(new DefaultArtifact("commons-codec", "commons-codec", "jar", COMMONS_CODEC_VERSION));
        testLocalRepositoryTwo.deployMockUpdate("commons-codec", "commons-codec", COMMONS_CODEC_VERSION, ".SP1");

        channels = List.of(
                new Channel.Builder()
                        .setName("test-channel")
                        .setGpgCheck(true)
                        .addGpgUrl(certFile.toURI().toString())
                        .addRepository("local-repo", testLocalRepositoryOne.getUri().toString())
                        .setManifestCoordinate(new ChannelManifestCoordinate("org.test", "test-channel"))
                        .build(),
                new Channel.Builder()
                        .setName("test-channel-two")
                        .addRepository("local-repo", testLocalRepositoryTwo.getUri().toString())
                        .setManifestCoordinate(new ChannelManifestCoordinate("org.test", "test-channel-two"))
                        .build()
        );

        serverPath = temp.newFolder("server").toPath();
        testInstallation = new TestInstallation(serverPath);
    }

    @Test
    public void installUpdateAndRevertUsingMixedChannels() throws Exception {
        // create FP with two modules
        final Artifact featurePack = TestInstallation.fpBuilder("org.test:pack-one:1.0.0")
                .addModule("commons-io", "commons-io", COMMONS_IO_VERSION)
                .addModule("commons-codec", "commons-codec", "1.17.1")
                .build();
        testLocalRepositoryOne.deploy(featurePack);

        // create two repositories - one with GPG signatures, one without
        testLocalRepositoryOne.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.0"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", GALLEON_PLUGINS_VERSION),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", GALLEON_PLUGINS_VERSION),
                        new Stream("commons-io", "commons-io", COMMONS_IO_VERSION1),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));
        testLocalRepositoryOne.signAllArtifacts(pgpValidKeys);
        testLocalRepositoryTwo.deploy(
                new DefaultArtifact("org.test", "test-channel-two", "manifest", "yaml","1.0.0"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("commons-codec", "commons-codec", COMMONS_CODEC_VERSION)
                )));


        // install the server
        testInstallation.install("org.test:pack-one:1.0.0", channels);

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_VERSION1);
        testInstallation.verifyModuleJar("commons-codec", "commons-codec", COMMONS_CODEC_VERSION);

        // perform update
        testLocalRepositoryOne.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.1"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", GALLEON_PLUGINS_VERSION),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", GALLEON_PLUGINS_VERSION),
                        new Stream("commons-io", "commons-io", COMMONS_IO_VERSION1 + ".SP1"),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));
        testLocalRepositoryOne.signAllArtifacts(pgpValidKeys);
        testLocalRepositoryTwo.deploy(
                new DefaultArtifact("org.test", "test-channel-two", "manifest", "yaml","1.0.1"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("commons-codec", "commons-codec", COMMONS_CODEC_VERSION + ".SP1")
                )));

        testInstallation.update();

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_VERSION1 + ".SP1");
        testInstallation.verifyModuleJar("commons-codec", "commons-codec", COMMONS_CODEC_VERSION + ".SP1");

        // perform revert to original state
        testInstallation.revertToOriginalState();

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_VERSION1);
        testInstallation.verifyModuleJar("commons-codec", "commons-codec", COMMONS_CODEC_VERSION);
    }
}
