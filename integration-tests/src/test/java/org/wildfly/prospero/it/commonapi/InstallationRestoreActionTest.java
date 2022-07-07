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

package org.wildfly.prospero.it.commonapi;

import org.wildfly.prospero.actions.InstallationExportAction;
import org.wildfly.prospero.actions.InstallationRestoreAction;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class InstallationRestoreActionTest extends WfCoreTestBase {
    private static final String FIRST_SERVER_DIR = "target/server";
    private static final Path FIRST_SERVER_PATH = Paths.get(FIRST_SERVER_DIR).toAbsolutePath();
    private static final String RESTORED_SERVER_DIR = "target/restored";
    private static final Path RESTORED_SERVER_PATH = Paths.get(RESTORED_SERVER_DIR).toAbsolutePath();

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
        final Path provisionConfigFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        new ProvisioningAction(FIRST_SERVER_PATH, mavenSessionManager, new AcceptingConsole()).provision(provisioningDefinition);

        MetadataTestUtils.prepareProvisionConfigAsUrl(FIRST_SERVER_PATH.resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);

        new InstallationExportAction(FIRST_SERVER_PATH).export("target/bundle.zip");

        new InstallationRestoreAction(RESTORED_SERVER_PATH, mavenSessionManager, new AcceptingConsole()).restore(Paths.get("target/bundle.zip"));

        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException {
        final File manifestFile = RESTORED_SERVER_PATH.resolve(MetadataTestUtils.MANIFEST_FILE_PATH).toFile();
        return ManifestYamlSupport.parse(manifestFile).getStreams()
                .stream().filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }
}
