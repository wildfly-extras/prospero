/*
 *
 *  * Copyright 2022 Red Hat, Inc. and/or its affiliates
 *  * and other contributors as indicated by the @author tags.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.wildfly.prospero.it.installationmanager;

import org.junit.Test;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.cli.DistributionInfo;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.commonapi.WfCoreTestBase;
import org.wildfly.prospero.spi.ProsperoInstallationManager;
import org.wildfly.prospero.spi.ProsperoInstallationManagerFactory;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CreateSnapshotTest extends WfCoreTestBase {

    @Test
    public void createSnapshot() throws Exception {
        // installCore
        Path channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        final InstallationManager manager = new ProsperoInstallationManagerFactory().create(outputPath, new MavenOptions(MavenSessionManager.LOCAL_MAVEN_REPO, false));

        // generate snapshot
        final Path snapshot = manager.createSnapshot(temp.newFolder().toPath());
        assertTrue(Files.exists(snapshot));

        // import snapshot
        final InstallationMetadata installationMetadata = InstallationMetadata.fromMetadataBundle(snapshot);
        assertEquals(1, installationMetadata.getProsperoConfig().getChannels().size());
    }

    @Test
    public void generateApplyCommand() throws Exception {
        final Path channelsFile = MetadataTestUtils.prepareChannel(CHANNEL_BASE_CORE_19);
        final StringBuffer expected = new StringBuffer("\"")
                .append(outputPath.resolve("bin").resolve(DistributionInfo.DIST_NAME));
        if (System.getProperty("os.name").toLowerCase(Locale.getDefault()).contains("windows")) {
            expected.append(".bat");
        } else {
            expected.append(".sh");
        }
        expected.append("\"");
        expected.append(" ");
        expected.append(CliConstants.Commands.UPDATE).append(" ").append(CliConstants.Commands.APPLY);
        expected.append(" ");
        expected.append(CliConstants.DIR).append("=\"").append(outputPath.toAbsolutePath()).append("\"");
        expected.append(" ");
        expected.append(CliConstants.UPDATE_DIR).append("=\"").append(Path.of("foo").toAbsolutePath()).append("\"");
        expected.append(" ");
        expected.append(CliConstants.YES);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();

        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));


        final ProsperoInstallationManager manager = (ProsperoInstallationManager) new ProsperoInstallationManagerFactory().create(outputPath, new MavenOptions(MavenSessionManager.LOCAL_MAVEN_REPO, false));
        final String command = manager.generateApplyUpdateCommand(outputPath.resolve("bin"), Paths.get("foo"));
        assertEquals(expected.toString(), command);
    }
}
