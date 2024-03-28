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

import org.jboss.galleon.Constants;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.CliMessages;
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
    protected static final Path MODULE_PATH = Path.of("modules", "com", "mysql", "jdbc");
    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private File targetDir;

    @Before
    public void setUp() throws IOException {
        targetDir = tempDir.newFolder("wildfly");
    }

    @Test
    public void addFeaturePack() throws Exception {
        installWildfly();

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
        assertThat(targetDir.toPath().resolve(MODULE_PATH))
                .exists()
                .isDirectory();

        assertThat(targetDir.toPath().resolve(MODULE_PATH).resolve("main").resolve("mysql-connector-j-8.0.32.jar"))
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

        assertThat(targetDir.toPath().resolve(MODULE_PATH))
                .doesNotExist();
    }

    @Test
    public void datasourcesFeaturePackRequiresLayers() throws Exception {
        installWildfly();

        final Path manifestPath = generateDatasourcesManifest();

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
                        CliConstants.YES,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.INVALID_ARGUMENTS)
                .assertErrorContains(CliMessages.MESSAGES.featurePackRequiresLayers(DATASOURCE_GALLEON_FPL));
    }

    @Test
    public void installWildflyGalleonPackOverWildflyEEGalleonPack_ReplacesWildflyGalleonPack() throws Exception {
        System.out.println("Installing wildfly EE");
        final Path channelsFile = MetadataTestUtils.prepareChannel("manifests/wildfly-28.0.0.Final-manifest.yaml");
        ExecutionUtils.prosperoExecution(CliConstants.Commands.INSTALL,
                        CliConstants.CHANNELS, channelsFile.toString(),
                        CliConstants.FPL, "org.wildfly:wildfly-ee-galleon-pack",
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        assertThat(targetDir.toPath().resolve(MODULE_PATH))
                .doesNotExist();

        // attempt to install wildfly feature pack with some layers
        System.out.println("Installing wildfly-galleon-pack feature pack with layers shouldn't work");
        ExecutionUtils.prosperoExecution(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                        CliConstants.FPL, "org.wildfly:wildfly-galleon-pack",
                        CliConstants.LAYERS, "test",
                        CliConstants.YES,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.INVALID_ARGUMENTS)
                .assertErrorContains(CliMessages.MESSAGES.featurePackDoesNotSupportCustomization("org.wildfly:wildfly-galleon-pack"));

        // install the datasource FP
        System.out.println("Installing wildfly-galleon-pack feature pack");
        ExecutionUtils.prosperoExecution(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                        CliConstants.FPL, "org.wildfly:wildfly-galleon-pack",
                        CliConstants.YES,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the datasources feature pack was installed successfully
        final ProvisioningConfig provisioningConfig = ProvisioningXmlParser.parse(targetDir.toPath().resolve(Constants.PROVISIONED_STATE_DIR).resolve(Constants.PROVISIONING_XML));

        assertThat(provisioningConfig.getFeaturePackDeps())
                .map(FeaturePackConfig::getLocation)
                .containsOnly(FeaturePackLocation.fromString("org.wildfly:wildfly-galleon-pack::zip@maven"));
        assertThat(provisioningConfig.getTransitiveDeps())
                .isEmpty();
    }

    private void installWildfly() throws Exception {
        System.out.println("Installing wildfly");
        final Path channelsFile = MetadataTestUtils.prepareChannel("manifests/wildfly-28.0.0.Final-manifest.yaml");
        ExecutionUtils.prosperoExecution(CliConstants.Commands.INSTALL,
                        CliConstants.CHANNELS, channelsFile.toString(),
                        CliConstants.PROFILE, "wildfly",
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        assertThat(targetDir.toPath().resolve(MODULE_PATH))
                .doesNotExist();
    }

    private Path generateDatasourcesManifest() throws IOException {
        final ChannelManifest manifest = new ChannelManifest(null, null, null, List.of(
                new Stream("org.wildfly", "wildfly-datasources-galleon-pack", DATASOURCES_FP_VERSION),
                new Stream("com.mysql", "mysql-connector-j", MYSQL_CONNECTOR_VERSION))
        );
        final Path manifestPath = tempDir.newFile("datasources-manifest.yaml").toPath();
        Files.writeString(manifestPath, ChannelManifestMapper.toYaml(manifest));
        return manifestPath;
    }
}
