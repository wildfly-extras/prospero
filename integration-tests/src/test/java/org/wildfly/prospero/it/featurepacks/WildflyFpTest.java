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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class WildflyFpTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path targetDir;

    @Before
    public void setUp() throws IOException {
        targetDir = tempDir.newFolder().toPath();
    }

    @Test
    public void testInstallProsperoWithWildfly() throws Exception {
        Path provisionConfig = tempDir.newFile().toPath();
        MetadataTestUtils.prepareProvisionConfig(provisionConfig, "channels/wildfly-27.0.0.Alpha2-channel.yaml", "channels/prospero-channel.yaml");

        final URL provisionDefinition = this.getClass().getClassLoader().getResource("galleon/wfcore-prospero.xml");

        ExecutionUtils.prosperoExecution(CliConstants.Commands.INSTALL,
                        CliConstants.PROVISION_CONFIG, provisionConfig.toString(),
                        CliConstants.DEFINITION, Paths.get(provisionDefinition.toURI()).toString(),
                        CliConstants.DIR, targetDir.toAbsolutePath().toString())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        final Path installedProspero = targetDir.resolve("bin").resolve(ExecutionUtils.isWindows()?"prospero.bat":"prospero.sh");
        assertTrue(Files.exists(installedProspero));

        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.DRY_RUN,
                CliConstants.DIR, targetDir.toAbsolutePath().toString())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute(installedProspero)
                .assertReturnCode(ReturnCodes.SUCCESS);
    }
}
