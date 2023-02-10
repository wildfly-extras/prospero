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

package org.wildfly.prospero.wfchannel;

import org.junit.Test;
import org.wildfly.prospero.api.MavenOptions;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class MavenSessionManagerTest {

    @Test
    public void defaultToMavenHome() throws Exception {
        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.DEFAULT_OPTIONS);
        assertEquals(MavenSessionManager.LOCAL_MAVEN_REPO, msm.getProvisioningRepo());
    }

    @Test
    public void useTempFolderIfNoCacheOptionSet() throws Exception {
        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.builder().setNoLocalCache(true).build());

        // JDK 17 reads the java.io.tmpdir property before it's altered by mvn
        final Path test = Files.createTempDirectory("test");
        final Path defaultTempPath = test.getParent();
        Files.delete(test);

        assertTrue(msm.getProvisioningRepo().toString() + " should start with  " + defaultTempPath, msm.getProvisioningRepo().startsWith(defaultTempPath));
    }
}