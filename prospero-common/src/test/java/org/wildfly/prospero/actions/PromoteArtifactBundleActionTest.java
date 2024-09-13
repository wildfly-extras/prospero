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

package org.wildfly.prospero.actions;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.api.ArtifactUtils;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.ProvisioningProgressEvent;
import org.wildfly.prospero.promotion.ArtifactBundle;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PromoteArtifactBundleActionTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void channelCoordinateMustHaveGA() throws Exception {
        try {
            new PromoteArtifactBundleAction(new TestConsole()).promote(createCustomArchive(), new URL("file://test/test-repo"),
                    ArtifactUtils.manifestCoordFromString("file://test/test.zip"));
            fail("URL Channel GA should not be allowed");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Channel reference has to use Maven GA."));
        }
    }

    @Test
    public void promoteArtifactsFromArchive() throws Exception {
        final PromoteArtifactBundleAction action = new PromoteArtifactBundleAction(new TestConsole());
        final Path targetRepo = temp.newFolder().toPath();

        action.promote(createCustomArchive(), targetRepo.toUri().toURL(),
                ArtifactUtils.manifestCoordFromString("org.test:test-channel"));

        assertTrue(Files.exists(targetRepo.resolve(Paths.get("foo", "bar", "test", "1.2.3", "test-1.2.3.jar"))));
    }

    private Path createCustomArchive() throws Exception {
        final DefaultArtifact testArtifact = new DefaultArtifact("foo.bar", "test", null, null, "1.2.3", null, temp.newFile("test-1.2.3.jar"));
        return ArtifactBundle.createCustomizationArchive(Collections.singletonList(testArtifact), temp.newFile("archive.zip"));
    }

    private static class TestConsole implements Console {

        @Override
        public void progressUpdate(ProvisioningProgressEvent update) {

        }

        @Override
        public void println(String text) {

        }

        @Override
        public boolean acceptPublicKey(String key) {
            return false;
        }
    }
}