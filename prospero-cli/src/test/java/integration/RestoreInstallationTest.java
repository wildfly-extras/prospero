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

import com.redhat.prospero.cli.actions.InstallationRestore;
import com.redhat.prospero.cli.actions.GalleonProvision;
import com.redhat.prospero.model.ManifestXmlSupport;
import com.redhat.prospero.model.XmlException;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class RestoreInstallationTest {

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

//    @After
//    public void tearDown() throws Exception {
//        if (FIRST_SERVER_PATH.toFile().exists()) {
//            FileUtils.deleteDirectory(FIRST_SERVER_PATH.toFile());
//            FIRST_SERVER_PATH.toFile().delete();
//        }
//
//        if (RESTORED_SERVER_PATH.toFile().exists()) {
//            FileUtils.deleteDirectory(RESTORED_SERVER_PATH.toFile());
//            RESTORED_SERVER_PATH.toFile().delete();
//        }
//    }

    @Test
    public void restoreInstallation() throws Exception {
        final Path channelFile = TestUtil.prepareChannelFile("local-repo-desc.json");
        new GalleonProvision().installFeaturePack("org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final", FIRST_SERVER_PATH.toString(), channelFile.toString());

        final Path manifestFilePath = FIRST_SERVER_PATH.resolve("manifest.xml");
        final Path restoreChannelsFile = Files.createTempFile("channels", "json");
        channelFile.toFile().deleteOnExit();
        TestUtil.prepareChannelFile(restoreChannelsFile, "local-repo-desc.json", "local-updates-repo-desc.json");
        new InstallationRestore().restore(manifestFilePath, restoreChannelsFile, RESTORED_SERVER_PATH, "org.wildfly.core:wildfly-core-galleon-pack:17.0.0.Final");

        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals("17.0.0.Final", wildflyCliArtifact.get().getVersion());
    }

    // TODO: need to provide the Galleon configuration as well

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws XmlException {
        final File manifestFile = RESTORED_SERVER_PATH.resolve("manifest.xml").toFile();
        return ManifestXmlSupport.parse(manifestFile).getArtifacts().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst();
    }
}
