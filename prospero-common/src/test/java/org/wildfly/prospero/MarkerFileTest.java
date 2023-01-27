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

package org.wildfly.prospero;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.actions.ApplyCandidateAction;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.updates.MarkerFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class MarkerFileTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testMe() throws Exception {
        final Path testFile = temp.newFolder().toPath();
        Files.createDirectory(testFile.resolve(ProsperoMetadataUtils.METADATA_DIR));
        final MarkerFile originalMarker = new MarkerFile("abcd12344", ApplyCandidateAction.Type.UPDATE);
        originalMarker.write(testFile);

        final MarkerFile readMarker = MarkerFile.read(testFile);
        assertEquals(originalMarker, readMarker);
    }
}
