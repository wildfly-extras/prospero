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

package com.redhat.prospero.installation;

import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.api.SavedState;
import com.redhat.prospero.model.ManifestXmlSupport;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LocalInstallationHistoryTest {

    @Test
    public void getHistoryOfInstallations() throws Exception {
        //create installation folder with simple metadata
        final Path installation = Files.createTempDirectory("installation");
        installation.toFile().deleteOnExit();
        try (FileWriter fw = new FileWriter(installation.resolve("channels.json").toFile())) {
            fw.write("[]");
        }
        final Manifest manifest = new Manifest(Arrays.asList(new DefaultArtifact("foo:bar:1.1.1")), installation.resolve("manifest.xml"));
        ManifestXmlSupport.write(manifest);

        LocalInstallation inst = new LocalInstallation(installation);
        final InstallationMetadata metadata = inst.getMetadata();

        metadata.writeFiles();
        List<SavedState> history = metadata.getRevisions();

        assertEquals(1, history.size());
    }
}
