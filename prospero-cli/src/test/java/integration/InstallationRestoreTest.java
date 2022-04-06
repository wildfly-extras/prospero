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

package integration;

import com.redhat.prospero.actions.InstallationExport;
import com.redhat.prospero.actions.InstallationRestore;
import com.redhat.prospero.actions.Installation;
import com.redhat.prospero.api.ProvisioningDefinition;
import com.redhat.prospero.cli.CliConsole;
import com.redhat.prospero.model.ManifestYamlSupport;
import com.redhat.prospero.wfchannel.MavenSessionManager;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class InstallationRestoreTest extends WfCoreTestBase {
    private static final String FIRST_SERVER_DIR = "target/server";
    private static final Path FIRST_SERVER_PATH = Paths.get(FIRST_SERVER_DIR).toAbsolutePath();

    private static final String RESTORED_SERVER_DIR = "target/restored";
    private static final Path RESTORED_SERVER_PATH = Paths.get(RESTORED_SERVER_DIR).toAbsolutePath();

    private MavenSessionManager mavenSessionManager = new MavenSessionManager();

    public InstallationRestoreTest() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        if (FIRST_SERVER_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(FIRST_SERVER_PATH.toFile());
            FIRST_SERVER_PATH.toFile().delete();
        }

        if (RESTORED_SERVER_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(RESTORED_SERVER_PATH.toFile());
            RESTORED_SERVER_PATH.toFile().delete();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (FIRST_SERVER_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(FIRST_SERVER_PATH.toFile());
            FIRST_SERVER_PATH.toFile().delete();
        }

        if (RESTORED_SERVER_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(RESTORED_SERVER_PATH.toFile());
            RESTORED_SERVER_PATH.toFile().delete();
        }
    }

    @Test
    public void restoreInstallation() throws Exception {
        final Path channelFile = TestUtil.prepareChannelFile("local-repo-desc.yaml");

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelsFile(channelFile)
                .build();
        new Installation(FIRST_SERVER_PATH, mavenSessionManager, new CliConsole()).provision(provisioningDefinition);

        TestUtil.prepareChannelFileAsUrl(FIRST_SERVER_PATH.resolve(TestUtil.CHANNELS_FILE_PATH), "local-repo-desc.yaml", "local-updates-repo-desc.yaml");

        new InstallationExport(FIRST_SERVER_PATH).export("target/bundle.zip");

        new InstallationRestore(RESTORED_SERVER_PATH).restore(Paths.get("target/bundle.zip"));

        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.0.Final", wildflyCliArtifact.get().getVersion());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException {
        final File manifestFile = RESTORED_SERVER_PATH.resolve(TestUtil.MANIFEST_FILE_PATH).toFile();
        return ManifestYamlSupport.parse(manifestFile).getArtifacts().stream().filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId)).findFirst();
    }
}
