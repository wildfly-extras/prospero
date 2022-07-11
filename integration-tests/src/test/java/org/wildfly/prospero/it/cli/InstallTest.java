/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.it.cli;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.cli.CliMain;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.test.MetadataTestUtils;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

public class InstallTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    File targetDir;

    @Before
    public void setUp() throws IOException {
        targetDir = tempDir.newFolder();
    }

    @Test
    public void testInstallWithProvisionConfig() throws IOException {
        URL provisionConfig = MetadataTestUtils.prepareProvisionConfigAsUrl("channels/wfcore-19-base.yaml");

        CommandLine commandLine = CliMain.createCommandLine(new AcceptingConsole());
        int returnCode = commandLine.execute(CliConstants.INSTALL,
                CliConstants.PROVISION_CONFIG, provisionConfig.getPath(),
                CliConstants.FPL, "wildfly-core@maven(org.jboss.universe:community-universe):19.0",
                CliConstants.REMOTE_REPOSITORIES,
                "https://repo1.maven.org/maven2/,https://repository.jboss.org/nexus/content/groups/public-jboss",
                CliConstants.DIR, targetDir.getAbsolutePath());

        assertThat(returnCode).isEqualTo(ReturnCodes.SUCCESS);
    }
}
