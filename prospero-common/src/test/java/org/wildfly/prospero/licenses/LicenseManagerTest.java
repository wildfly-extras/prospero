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

package org.wildfly.prospero.licenses;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LicenseManagerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void noLicenseFileDoesNothing() throws Exception {
        assertThat(new LicenseManager(null).getLicenses(List.of("test:test")))
                .isEmpty();
    }

    @Test
    public void emptyListIfNoLicensesMatched() throws Exception {
        final File licenseFile = temp.newFile();
        License.writeLicenses(List.of(new License("name", "foo:bar", "title", "text")), licenseFile);
        assertThat(new LicenseManager(licenseFile.toURI().toURL()).getLicenses(List.of("test:test")))
                .isEmpty();
    }

    @Test
    public void matchesLicenseByFpGav() throws Exception {
        final File licenseFile = temp.newFile();
        final License l1 = new License("name", "test:test", "title", "text");
        License.writeLicenses(List.of(l1), licenseFile);
        assertThat(new LicenseManager(licenseFile.toURI().toURL()).getLicenses(List.of("test:test")))
                .contains(l1);
    }

    @Test
    public void matchesMultipleLicenseByFpGav() throws Exception {
        final File licenseFile = temp.newFile();
        final License l1 = new License("name", "test:test", "title", "text");
        final License l2 = new License("name2", "test:test", "title2", "text2");
        License.writeLicenses(List.of(l1, l2), licenseFile);
        assertThat(new LicenseManager(licenseFile.toURI().toURL()).getLicenses(List.of("test:test")))
                .contains(l1, l2);
    }
}