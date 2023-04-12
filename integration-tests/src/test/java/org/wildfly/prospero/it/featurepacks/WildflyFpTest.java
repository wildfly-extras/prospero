/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.it.featurepacks;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class WildflyFpTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path targetDir;

    private Path localRepo;

    @Before
    public void setUp() throws Exception {
        targetDir = tempDir.newFolder().toPath();
        localRepo = tempDir.newFolder().toPath();
        deployWildflyManifest();
    }

    @Test
    public void testInstallProsperoWithWildfly() throws Exception {
        Path channelsFile = tempDir.newFile().toPath();

        prepareWflyAndProsperoChannels(channelsFile);

        final URL provisionDefinition = this.getClass().getClassLoader().getResource("galleon/wfly-with-prospero.xml");

        ExecutionUtils.prosperoExecution(CliConstants.Commands.INSTALL,
                        CliConstants.CHANNELS, channelsFile.toString(),
                        CliConstants.DEFINITION, Paths.get(provisionDefinition.toURI()).toString(),
                        CliConstants.ACCEPT_AGREEMENTS,
                        CliConstants.DIR, targetDir.toAbsolutePath().toString())
                .withTimeLimit(20, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        final Path installedProspero = targetDir.resolve("bin").resolve(ExecutionUtils.isWindows()?"prospero.bat":"prospero.sh");
        assertTrue(Files.exists(installedProspero));

        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.LIST,
                CliConstants.DIR, targetDir.toAbsolutePath().toString())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute(installedProspero)
                .assertReturnCode(ReturnCodes.SUCCESS);
    }

    private void prepareWflyAndProsperoChannels(Path channelsFile) throws IOException {
        // read the settings from pom properties
        final Properties properties = new Properties();
        properties.load(this.getClass().getClassLoader().getResourceAsStream("properties-from-pom.properties"));
        final String groupId = properties.getProperty("prospero.test.base.channel.groupId");
        final String artifactId = properties.getProperty("prospero.test.base.channel.artifactId");
        final String testRepoUrls = properties.getProperty("prospero.test.base.repositories");

        // create channels
        Channel.Builder cb1 = new Channel.Builder()
                .setManifestCoordinate(new ChannelManifestCoordinate(groupId, artifactId))
                .addRepository("local-repo", localRepo.toUri().toString());
        Channel.Builder cb2 = new Channel.Builder()
                .setManifestUrl(this.getClass().getClassLoader().getResource("manifests/prospero-manifest.yaml"));

        final String[] urls = testRepoUrls.split(",");
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            cb1.addRepository("test-repo-" + i, url);
            cb2.addRepository("test-repo-" + i, url);
        }

        // export to channelsFile
        Files.writeString(channelsFile, ChannelMapper.toYaml(cb1.build(), cb2.build()));
    }

    private void deployWildflyManifest() throws Exception {
        final MavenSessionManager mavenSessionManager = new MavenSessionManager(MavenOptions.OFFLINE_NO_CACHE);
        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system);

        final File wfManifest = new File(this.getClass().getClassLoader().getResource("manifests/wildfly-27.0.1.Final-channel.yaml").toURI());

        final DeployRequest deployRequest = new DeployRequest();
        deployRequest.setRepository(new RemoteRepository.Builder("local-repo", "default", localRepo.toUri().toString()).build());
        deployRequest.addArtifact(new DefaultArtifact("org.wildfly.channels", "wildfly-27-test",
                "manifest", "yaml", "1.0.0.Final", null, wfManifest));
        system.deploy(session, deployRequest);
    }
}
