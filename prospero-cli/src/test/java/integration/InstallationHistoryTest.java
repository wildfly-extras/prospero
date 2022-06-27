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

import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.actions.Provision;
import org.wildfly.prospero.actions.InstallationHistory;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.actions.Update;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.ProvisioningException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InstallationHistoryTest extends WfCoreTestBase {

    private static final String OUTPUT_DIR = "target/server";
    private static final Path OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();
    private final Provision installation = new Provision(OUTPUT_PATH, mavenSessionManager, new CliConsole());
    private Path provisionConfigFile;

    @Before
    public void setUp() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
        if (Files.exists(provisionConfigFile)) {
            Files.delete(provisionConfigFile);
        }
    }

    @Test
    public void listUpdates() throws Exception {
        // installCore
        provisionConfigFile = TestUtil.prepareProvisionConfig("local-repo-desc.yaml");
        Path installDir = Paths.get(OUTPUT_PATH.toString());
        if (Files.exists(installDir)) {
            throw new ProvisioningException("Installation dir " + installDir + " already exists");
        }

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        installation.provision(provisioningDefinition);

        // updateCore
        TestUtil.prepareProvisionConfigAsUrl(OUTPUT_PATH.resolve(TestUtil.PROVISION_CONFIG_FILE_PATH), "local-updates-repo-desc.yaml", "local-repo-desc.yaml");
        new Update(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole()).doUpdateAll(false);

        // get history
        List<SavedState> states = new InstallationHistory(OUTPUT_PATH, new AcceptingConsole()).getRevisions();

        // assert two entries
        assertEquals(2, states.size());
    }

    @Test
    public void rollbackChanges() throws Exception {
        provisionConfigFile = TestUtil.prepareProvisionConfig("local-repo-desc.yaml");
        final Path modulesPaths = OUTPUT_PATH.resolve(Paths.get("modules", "system", "layers", "base"));
        final Path wildflyCliModulePath = modulesPaths.resolve(Paths.get("org", "jboss", "as", "cli", "main"));

        if (Files.exists(OUTPUT_PATH)) {
            throw new ProvisioningException("Installation dir " + OUTPUT_PATH + " already exists");
        }

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        installation.provision(provisioningDefinition);

        TestUtil.prepareProvisionConfigAsUrl(OUTPUT_PATH.resolve(TestUtil.PROVISION_CONFIG_FILE_PATH), "local-updates-repo-desc.yaml", "local-repo-desc.yaml");
        new Update(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole()).doUpdateAll(false);
        Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(UPGRADE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Updated jar should be present in module", wildflyCliModulePath.resolve(UPGRADE_JAR).toFile().exists());

        final InstallationHistory installationHistory = new InstallationHistory(OUTPUT_PATH, new AcceptingConsole());
        final List<SavedState> revisions = installationHistory.getRevisions();

        final SavedState savedState = revisions.get(1);
        installationHistory.rollback(savedState, mavenSessionManager);

        wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
        assertTrue("Reverted jar should be present in module", wildflyCliModulePath.resolve(BASE_JAR).toFile().exists());
    }

    @Test
    public void displayChanges() throws Exception {
        provisionConfigFile = TestUtil.prepareProvisionConfig("local-repo-desc.yaml");
        Path installDir = Paths.get(OUTPUT_PATH.toString());
        if (Files.exists(installDir)) {
            throw new ProvisioningException("Installation dir " + installDir + " already exists");
        }

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        installation.provision(provisioningDefinition);

        TestUtil.prepareProvisionConfigAsUrl(OUTPUT_PATH.resolve(TestUtil.PROVISION_CONFIG_FILE_PATH), "local-updates-repo-desc.yaml", "local-repo-desc.yaml");
        new Update(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole()).doUpdateAll(false);

        final InstallationHistory installationHistory = new InstallationHistory(OUTPUT_PATH, new AcceptingConsole());
        final List<SavedState> revisions = installationHistory.getRevisions();

        final SavedState savedState = revisions.get(1);
        final List<ArtifactChange> changes = installationHistory.compare(savedState);

        for (ArtifactChange change : changes) {
            System.out.println(change);
        }

        assertEquals(1, changes.size());

        Map<String, String[]> expected = new HashMap<>();
        expected.put("org.wildfly.core:wildfly-cli", new String[]{BASE_VERSION, UPGRADE_VERSION});

        for (ArtifactChange change : changes) {
            if (expected.containsKey(change.getArtifactName())) {
                final String[] versions = expected.get(change.getArtifactName());
                assertEquals(versions[1], change.getNewVersion().get());
                expected.remove(change.getArtifactName());
            } else {
                Assert.fail("Unexpected artifact in updates " + change);
            }
        }
        assertEquals("Not all expected changes were listed", 0, expected.size());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException {
        final File manifestFile = OUTPUT_PATH.resolve(TestUtil.MANIFEST_FILE_PATH).toFile();
        return ManifestYamlSupport.parse(manifestFile).getStreams()
                .stream().filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }
}
