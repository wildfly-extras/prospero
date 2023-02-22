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

package org.wildfly.prospero.model;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.wildfly.prospero.metadata.ProsperoMetadataUtils.CURRENT_VERSION_FILE;

public class ManifestVersionRecordTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void serializeTest() throws Exception {
        ManifestVersionRecord.NoManifest noManifest = new ManifestVersionRecord.NoManifest(List.of("central", "nexus"), "LATEST");
        ManifestVersionRecord.UrlManifest urlManifest = new ManifestVersionRecord.UrlManifest("http://foo.bar", "abcd1234");
        ManifestVersionRecord.MavenManifest versionManifest = new ManifestVersionRecord.MavenManifest("foo", "bar", "1.0.0");
        final ManifestVersionRecord manifestVersionRecord = new ManifestVersionRecord();
        manifestVersionRecord.addManifest(noManifest);
        manifestVersionRecord.addManifest(urlManifest);
        manifestVersionRecord.addManifest(versionManifest);

        final Path installDir = temp.newFolder().toPath();
        ManifestVersionRecord.write(manifestVersionRecord, installDir.resolve(CURRENT_VERSION_FILE));

        final Optional<ManifestVersionRecord> read = ManifestVersionRecord.read(installDir.resolve(CURRENT_VERSION_FILE));
        assertEquals(manifestVersionRecord.getSummary(), read.get().getSummary());
    }

    @Test
    public void readingNonExistingFileReturnsEmpty() throws Exception {
        final Path installDir = temp.newFolder().toPath();
        assertEquals(Optional.empty(), ManifestVersionRecord.read(installDir.resolve(CURRENT_VERSION_FILE)));
    }

}