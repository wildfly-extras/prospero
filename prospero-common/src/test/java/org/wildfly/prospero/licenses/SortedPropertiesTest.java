/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.licenses;

import static org.assertj.core.api.Assertions.*;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SortedPropertiesTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void writeSortedProperties() throws Exception {
        final SortedProperties properties = new SortedProperties();

        properties.setProperty("bbb", "bar");
        properties.setProperty("aaa", "foo");
        properties.setProperty("ddd", "212");
        properties.setProperty("ccc", "123");

        final Path propertiesFile = temp.newFile().toPath();
        try (FileOutputStream fos = new FileOutputStream(propertiesFile.toFile())) {
            properties.store(fos, null);
        }

        // first line is going to be a comment with timestamp, need to add it to the check
        final String firstLine = Files.readAllLines(propertiesFile).get(0);
        assertThat(firstLine)
                .startsWith("#");
        assertThat(propertiesFile)
                .hasContent(firstLine + "\naaa=foo\nbbb=bar\nccc=123\nddd=212");

    }
}