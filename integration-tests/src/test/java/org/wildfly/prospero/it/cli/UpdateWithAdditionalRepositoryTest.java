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

package org.wildfly.prospero.it.cli;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.deployment.DeployRequest;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.it.commonapi.WfCoreTestBase;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class UpdateWithAdditionalRepositoryTest extends WfCoreTestBase {

    private File targetDir;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        targetDir = temp.newFolder();
    }

    @Test
    public void updateCli() throws Exception {
        Path provisionConfig = MetadataTestUtils.prepareProvisionConfig("channels/wfcore-19-base.yaml");

        install(provisionConfig);

        final ProsperoConfig prosperoConfig = ProsperoConfig.readConfig(targetDir.toPath().resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH));
        // TODO: replace with GA channel
        final URL modifiedChannel = this.getClass().getClassLoader().getResource("channels/wfcore-19-upgrade-component.yaml");
        prosperoConfig.addChannel(new ChannelRef(null, modifiedChannel.toString()));
        prosperoConfig.writeConfig(targetDir.toPath().resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH).toFile());
        URL internalRepo = mockInternalRepo();

        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE,
                        CliConstants.REMOTE_REPOSITORIES, internalRepo.toString(),
                        CliConstants.Y,
                        CliConstants.NO_LOCAL_MAVEN_CACHE,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        final Optional<Stream> wildflyCliStream = ManifestYamlSupport.parse(targetDir.toPath().resolve(MetadataTestUtils.MANIFEST_FILE_PATH).toFile())
                .getStreams().stream()
                .filter(s -> s.getArtifactId().equals("wildfly-cli"))
                .findFirst();

        assertEquals(WfCoreTestBase.UPGRADE_VERSION, wildflyCliStream.get().getVersion());
    }

    private URL mockInternalRepo() throws Exception {
        final File repo = temp.newFolder();
        final URL repoUrl = repo.toURI().toURL();
        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system);

        final DeployRequest deployRequest = new DeployRequest();
        deployRequest.addArtifact(resolvedUpgradeArtifact);
        deployRequest.addArtifact(resolvedUpgradeClientArtifact);
        deployRequest.setRepository(new RepositoryRef("test", repoUrl.toString()).toRemoteRepository());
        system.deploy(session, deployRequest);

        return repoUrl;
    }

    private void install(Path provisionConfig) throws Exception {
        ExecutionUtils.prosperoExecution(CliConstants.Commands.INSTALL,
                        CliConstants.PROVISION_CONFIG, provisionConfig.toString(),
                        CliConstants.FPL, "wildfly-core@maven(org.jboss.universe:community-universe):19.0",
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);
    }
}
