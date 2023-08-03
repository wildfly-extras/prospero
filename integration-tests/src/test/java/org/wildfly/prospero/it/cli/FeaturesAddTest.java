/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class FeaturesAddTest {

    protected static final String DATASOURCE_GALLEON_FPL = "org.wildfly:wildfly-datasources-galleon-pack";
    protected static final String DATASOURCES_FP_VERSION = "4.0.0.Final";
    protected static final String MYSQL_CONNECTOR_VERSION = "8.0.32";
    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private File targetDir;

    @Before
    public void setUp() throws IOException {
        targetDir = tempDir.newFolder("wildfly");
    }

    @Test
    public void addFeaturePack() throws Exception {
        final Path channelsFile = MetadataTestUtils.prepareChannel("manifests/wildfly-27.0.1.Final-channel.yaml");
        final Path modulePath = Path.of("modules", "com", "mysql", "jdbc");

        System.out.println("Installing wildfly");
        ExecutionUtils.prosperoExecution(CliConstants.Commands.INSTALL,
                        CliConstants.CHANNELS, channelsFile.toString(),
                        CliConstants.PROFILE, "wildfly",
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        assertThat(targetDir.toPath().resolve(modulePath))
                .doesNotExist();

        final ChannelManifest manifest = new ChannelManifest(null, null, null, List.of(
                new Stream("org.wildfly", "wildfly-datasources-galleon-pack", DATASOURCES_FP_VERSION),
                new Stream("com.mysql", "mysql-connector-j", MYSQL_CONNECTOR_VERSION))
        );
        final Path manifestPath = tempDir.newFile("datasources-manifest.yaml").toPath();
        Files.writeString(manifestPath, ChannelManifestMapper.toYaml(manifest));

        System.out.println("Adding datasources channel");
        ExecutionUtils.prosperoExecution(CliConstants.Commands.CHANNEL, CliConstants.Commands.ADD,
                        CliConstants.CHANNEL_NAME, "datasources",
                        CliConstants.REPOSITORIES, "central::https://repo1.maven.org/maven2",
                        CliConstants.CHANNEL_MANIFEST, manifestPath.toUri().toURL().toExternalForm(),
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // install the datasource FP
        System.out.println("Installing datasources feature pack");
        ExecutionUtils.prosperoExecution(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                        CliConstants.FPL, DATASOURCE_GALLEON_FPL,
                        CliConstants.LAYERS, "datasources-web-server,mysql-datasource",
                        CliConstants.YES,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the datasources feature pack was installed successfully
        assertThat(targetDir.toPath().resolve(modulePath))
                .exists()
                .isDirectory();

        assertThat(targetDir.toPath().resolve(modulePath).resolve("main").resolve("mysql-connector-j-8.0.32.jar"))
                .exists()
                .isNotEmptyFile();

        // rollback the datasources feature pack

        System.out.println("Getting the previous state from history");
        final SavedState savedState = new InstallationHistoryAction(targetDir.toPath(), null).getRevisions().get(1);
        System.out.println("Rolling back the datasources feature pack");
        ExecutionUtils.prosperoExecution(CliConstants.Commands.REVERT, CliConstants.Commands.PERFORM,
                        CliConstants.REVISION, savedState.getName(),
                        CliConstants.YES,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        assertThat(targetDir.toPath().resolve(modulePath))
                .doesNotExist();
    }
}
