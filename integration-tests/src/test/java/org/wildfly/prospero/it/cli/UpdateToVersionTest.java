package org.wildfly.prospero.it.cli;

import static org.wildfly.prospero.test.TestLocalRepository.GALLEON_PLUGINS_VERSION;

import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.test.BuildProperties;
import org.wildfly.prospero.test.TestInstallation;
import org.wildfly.prospero.test.TestLocalRepository;

public class UpdateToVersionTest extends CliTestBase {
    protected static final String COMMONS_IO_VERSION = BuildProperties.getProperty("version.commons-io");

    private TestLocalRepository testLocalRepository;
    private TestInstallation testInstallation;
    private Channel testChannel;

    @Before
    public void setUp() throws Exception {
        testLocalRepository = new TestLocalRepository(temp.newFolder("local-repo").toPath(),
                List.of(new URL("https://repo1.maven.org/maven2")));

        prepareRequiredArtifacts(testLocalRepository);

        testInstallation = new TestInstallation(temp.newFolder("server").toPath());

        testLocalRepository.deploy(TestInstallation.fpBuilder("org.test:pack-one:1.0.0")
                .addModule("commons-io", "commons-io", COMMONS_IO_VERSION)
                .build());

        testChannel = new Channel.Builder()
                .setName("test-channel")
                .addRepository("local-repo", testLocalRepository.getUri().toString())
                .setManifestCoordinate("org.test", "test-channel")
                .build();
    }

    @Test
    public void installSpecificVersionOfManifest() throws Exception {
        testInstallation.install("org.test:pack-one:1.0.0", List.of(testChannel), CliConstants.VERSION, "test-channel::1.0.0");

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_VERSION);
        testInstallation.verifyInstallationMetadataPresent();
    }

    @Test
    public void updateServerToNonLatestVersion() throws Exception {
        testInstallation.install("org.test:pack-one:1.0.0", List.of(testChannel), CliConstants.VERSION, "test-channel::1.0.0");

        testInstallation.update(CliConstants.VERSION, "test-channel::1.0.1");

        testInstallation.verifyModuleJar("commons-io", "commons-io", bump(COMMONS_IO_VERSION));
        testInstallation.verifyInstallationMetadataPresent();
    }

    @Test
    public void downgradeServerToNonLatestVersion() throws Exception {
        testInstallation.install("org.test:pack-one:1.0.0", List.of(testChannel), "--version=test-channel::1.0.1");

        testInstallation.update("--version=test-channel::1.0.0");

        testInstallation.verifyModuleJar("commons-io", "commons-io", COMMONS_IO_VERSION);
        testInstallation.verifyInstallationMetadataPresent();
    }

    private void prepareRequiredArtifacts(TestLocalRepository localRepository) throws Exception {
        localRepository.deployGalleonPlugins();

        Artifact resolved = localRepository.resolveAndDeploy(new DefaultArtifact("commons-io", "commons-io", "jar", COMMONS_IO_VERSION));
        resolved = resolved.setVersion(bump(resolved.getVersion()));
        localRepository.deploy(resolved);
        localRepository.deploy(resolved.setVersion(bump(resolved.getVersion())));

        // deploy test manifests updating the commons-io in each
        localRepository.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.0"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", GALLEON_PLUGINS_VERSION),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", GALLEON_PLUGINS_VERSION),
                        new Stream("commons-io", "commons-io", COMMONS_IO_VERSION),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));

        testLocalRepository.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.1"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", GALLEON_PLUGINS_VERSION),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", GALLEON_PLUGINS_VERSION),
                        new Stream("commons-io", "commons-io", bump(COMMONS_IO_VERSION)),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));

        testLocalRepository.deploy(
                new DefaultArtifact("org.test", "test-channel", "manifest", "yaml","1.0.2"),
                new ChannelManifest("test-manifest", null, null, List.of(
                        new Stream("org.wildfly.galleon-plugins", "wildfly-config-gen", GALLEON_PLUGINS_VERSION),
                        new Stream("org.wildfly.galleon-plugins", "wildfly-galleon-plugins", GALLEON_PLUGINS_VERSION),
                        new Stream("commons-io", "commons-io", bump(bump(COMMONS_IO_VERSION))),
                        new Stream("org.test", "pack-one", "1.0.0")
                )));
    }

    private String bump(String version) {
        final Pattern pattern = Pattern.compile(".*\\.CP-(\\d{2})");
        final Matcher matcher = pattern.matcher(version);
        if (matcher.matches()) {
            final String suffixVersion = matcher.group(1);
            final int ver = Integer.parseInt(suffixVersion) + 1;
            final String replaced = version.replace(".CP-" + suffixVersion, String.format(".CP-%02d", ver));
            return replaced;
        } else {
            return version + ".CP-01";
        }
    }
}
