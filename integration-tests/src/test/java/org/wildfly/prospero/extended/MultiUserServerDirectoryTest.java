package org.wildfly.prospero.extended;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.ProvisioningException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.DistributionInfo;
import org.wildfly.prospero.it.commonapi.WfCoreTestBase;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.test.MetadataTestUtils;

/**
 * Test using docker container to simulate multiple users accounts creating diectories and files
 * in the server directory.
 *
 * Note: this test will be skipped if docker env is not available
 */
public class MultiUserServerDirectoryTest extends WfCoreTestBase {

    private GenericContainer container;
    private ContainerFilesystem filesystem;
    private User siteadmin;
    private User serveradmin;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        // load prospero-dist created by the pom.xml dependency-plugin
        final URL resource = this.getClass().getClassLoader().getResource("prospero-dist");
        final Path prosperoDist = Path.of(resource.toURI());

        // TODO: replace URL-manifest provisioning with GA-manifest
        // TODO: also, create the wf-core distro only once per class to spead this up
        // right now it's a hacky way to provision/update wf-core
        // we start by provisioning the wf-core with standard manifest
        // then we modify the installer-channel.yaml to point to a manifest file we bundle in the docker container
        // finally we get the original manifest, change the version of one of components and add it to the container
        final File updatedManifest = temp.newFile("updated-manifest.yaml");
        prepareUpdateableWildFlyCoreDistro(updatedManifest, "file:/dist/manifest.yaml");

        container = new GenericContainer(new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder ->
                        builder
                                .from("fedora")
                                .run("dnf install -y java-17-openjdk-devel")
                                .run("mkdir -p /home/serveradmin && adduser serveradmin && adduser siteadmin")
                ))
                .withCommand("tail -f /dev/null")
                .withCopyFileToContainer(MountableFile.forHostPath(prosperoDist), "/dist/prospero")
                .withCopyFileToContainer(MountableFile.forHostPath(outputPath), "/dist/wildfly")
                .withCopyFileToContainer(MountableFile.forHostPath(updateRepositoryPath), "/dist/repository")
                .withCopyFileToContainer(MountableFile.forHostPath(updatedManifest.toPath()), "/dist/manifest.yaml")
                .withStartupTimeout(Duration.ofSeconds(120));

        container.start();
        filesystem = new ContainerFilesystem(container);

        // prepare the container by creating useraccounts and directory structure
        serveradmin = new User("serveradmin", container);
        siteadmin = new User("siteadmin", container);

        container.execInContainer("cp", "-r", "/dist/wildfly", "/home/serveradmin/");
        container.execInContainer("cp", "-r", "/dist/prospero", "/home/serveradmin/");
        container.execInContainer("cp", "-r", "/dist/repository", "/home/serveradmin/");
    }

    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        if (filesystem != null && filesystem.fileExists("/home/serveradmin/prospero/logs/installation.log")) {
            container.copyFileFromContainer("/home/serveradmin/prospero/logs/installation.log", "target/installation.log");
        }
        if (container != null) {
            container.stop();
        }
    }

    @Test
    public void updateRollbackDoesNotRemoveExternalFiles() throws Exception {
        // actual test setup - make the server and prospero owned by 'serveradmin'
        container.execInContainer("chmod", "0777", "serveradmin:serveradmin", "/home/serveradmin");
        container.execInContainer("chmod", "0777", "serveradmin:serveradmin", "/home/serveradmin/wildfly");
        container.execInContainer("chown", "-R", "serveradmin:serveradmin", "/home/serveradmin");

        // actual test setup - create the file owned by a different user
        siteadmin.execute("touch", "/home/serveradmin/wildfly/test");
        siteadmin.execute("mkdir", "/home/serveradmin/wildfly/test_dir");
        siteadmin.execute("touch", "/home/serveradmin/wildfly/test_dir/test");
        siteadmin.execute("chmod", "0755", "/home/serveradmin/wildfly/test");
        siteadmin.execute("chmod", "0755", "/home/serveradmin/wildfly/test_dir");
        siteadmin.execute("chmod", "0755", "/home/serveradmin/wildfly/test_dir/test");

        // make the apply fail on purpose
        container.execInContainer("chmod", "-R", "0555", "serveradmin:serveradmin", "/home/serveradmin/wildfly/modules");

        // finally perform update
        final Container.ExecResult updateRes = serveradmin.tryExecute("/home/serveradmin/prospero/bin/" + DistributionInfo.DIST_NAME + ".sh", "update", "perform",
                "--dir", "/home/serveradmin/wildfly", "--repositories", "/home/serveradmin/repository/", "-y", "-vv");
        assertEquals(updateRes.toString(), 1, updateRes.getExitCode());

        final Container.ExecResult res = container.execInContainer("ls", "-alF", "/home/serveradmin/wildfly");
        assertTrue(res.toString(), filesystem.fileExists("/home/serveradmin/wildfly/test"));
        assertTrue(res.toString(), filesystem.fileExists("/home/serveradmin/wildfly/test_dir"));
        assertTrue(res.toString(), filesystem.fileExists("/home/serveradmin/wildfly/test_dir/test"));
    }

    @Test
    public void updateServerWithAnExternalFilesNotOwnedByUser() throws Exception {
        // actual test setup - make the server and prospero owned by 'serveradmin'
        container.execInContainer("chmod", "0777", "serveradmin:serveradmin", "/home/serveradmin");
        container.execInContainer("chmod", "0777", "serveradmin:serveradmin", "/home/serveradmin/wildfly");
        container.execInContainer("chown", "-R", "serveradmin:serveradmin", "/home/serveradmin");

        // actual test setup - create the file owned by a different user
        siteadmin.execute("touch", "/home/serveradmin/wildfly/test");
        siteadmin.execute("mkdir", "/home/serveradmin/wildfly/test_dir");
        siteadmin.execute("touch", "/home/serveradmin/wildfly/test_dir/test");
        siteadmin.execute("chmod", "0755", "/home/serveradmin/wildfly/test");
        siteadmin.execute("chmod", "0755", "/home/serveradmin/wildfly/test_dir");
        siteadmin.execute("chmod", "0755", "/home/serveradmin/wildfly/test_dir/test");

        // finally perform update
        serveradmin.execute("/home/serveradmin/prospero/bin/" + DistributionInfo.DIST_NAME + ".sh", "update", "perform",
                "--dir", "/home/serveradmin/wildfly", "--repositories", "/home/serveradmin/repository/", "-y", "-vv");

        final Container.ExecResult res = container.execInContainer("ls", "-alF", "/home/serveradmin/wildfly");
        assertTrue(res.toString(), filesystem.fileExists("/home/serveradmin/wildfly/test"));
        assertTrue(res.toString(), filesystem.fileExists("/home/serveradmin/wildfly/test_dir"));
        assertTrue(res.toString(), filesystem.fileExists("/home/serveradmin/wildfly/test_dir/test"));
    }

    private void prepareUpdateableWildFlyCoreDistro(File updatedManifest, String containerManifestPath) throws IOException, ProvisioningException, OperationException {
        provisioninWildFlyCore();

        subscribeServerToUpdateManifest(containerManifestPath);

        createUpdateManifest(updatedManifest);
    }

    private void subscribeServerToUpdateManifest(String containerManifestPath) throws IOException {
        final Path channelDefinition = outputPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME);
        final Channel channel = ChannelMapper.from(channelDefinition.toUri().toURL());

        FileUtils.writeStringToFile(channelDefinition.toFile(), ChannelMapper.toYaml(new Channel.Builder(channel)
                .setManifestUrl(new URL(containerManifestPath))
                .build()));
    }

    private static void createUpdateManifest(File updatedManifest) throws IOException {
        final ChannelManifest originalManifest = ChannelManifestMapper.from(
                MultiUserServerDirectoryTest.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19)
        );

        final ChannelManifest.Builder manifestBuilder = new ChannelManifest.Builder();
        // bump wildfly-cli
        for (Stream s : originalManifest.getStreams()) {
            if (s.getGroupId().equals("org.wildfly.core") && s.getArtifactId().equals("wildfly-cli")) {
                manifestBuilder.addStreams(new Stream(s.getGroupId(), s.getArtifactId(), WfCoreTestBase.UPGRADE_VERSION));
            } else {
                manifestBuilder.addStreams(s);
            }
        }
        FileUtils.writeStringToFile(updatedManifest, ChannelManifestMapper.toYaml(manifestBuilder.build()));
    }

    private void provisioninWildFlyCore() throws IOException, ProvisioningException, OperationException {
        final Path channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));
    }



    static class ContainerFilesystem {
        private final GenericContainer container;

        public ContainerFilesystem(GenericContainer container) {
            this.container = container;
        }

        private boolean fileExists(String path) throws IOException, InterruptedException {
            final Container.ExecResult res = container.execInContainer("ls", path);
            return res.getExitCode() == 0;
        }
    }

    static class User{
        private final String name;
        private final Container container;

        public User(String name, Container container) {
            this.name = name;
            this.container = container;
        }

        public Container.ExecResult execute(String... cmd) throws IOException, InterruptedException {
            final Container.ExecResult res = tryExecute(cmd);

            assertEquals(res.toString(), 0, res.getExitCode());
            return res;
        }

        public Container.ExecResult tryExecute(String... cmd) throws IOException, InterruptedException {
            return container.execInContainer(
                    ExecConfig.builder()
                            .user(name)
                            .command(cmd)
                            .build());
        }
    }
}

