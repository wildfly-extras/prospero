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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.wildfly.prospero.licenses.LicenseManager.LICENSES_FOLDER;
import static org.wildfly.prospero.licenses.LicenseManager.LICENSE_AGREEMENT_FILENAME;
import static org.wildfly.prospero.licenses.LicenseManager.LICENSE_DEFINITION_NAME;

public class LicenseManagerTest {

    private static final String A_FEATURE_PACK = "test:test";
    private static final String UNKNOWN_FEATURE_PACK = "idont:exist";
    private static final License LICENSE_ONE = new License("name", A_FEATURE_PACK, "title", "text");
    private static final License LICENSE_TWO = new License("name2", A_FEATURE_PACK, "title2", "text2");

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private Path serverPath;
    private File licenseFile;

    @Before
    public void setUp() throws Exception {
        serverPath = temp.newFolder("server").toPath();
        Files.createDirectory(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR));
        licenseFile = temp.newFile(LICENSE_DEFINITION_NAME);
    }

    @Test
    public void noLicenseFileDoesNothing() throws Exception {
        assertThat(new LicenseManager(null).getLicenses(Set.of(A_FEATURE_PACK)))
                .isEmpty();
    }

    @Test
    public void emptyListIfNoLicensesMatched() throws Exception {
        License.writeLicenses(List.of(LICENSE_ONE), licenseFile);
        assertThat(new LicenseManager(licenseFile.toURI().toURL()).getLicenses(Set.of(UNKNOWN_FEATURE_PACK)))
                .isEmpty();
    }

    @Test
    public void matchesLicenseByFpGav() throws Exception {
        License.writeLicenses(List.of(LICENSE_ONE), licenseFile);
        assertThat(new LicenseManager(licenseFile.toURI().toURL()).getLicenses(Set.of(A_FEATURE_PACK)))
                .contains(LICENSE_ONE);
    }

    @Test
    public void matchesMultipleLicenseByFpGav() throws Exception {
        License.writeLicenses(List.of(LICENSE_ONE, LICENSE_TWO), licenseFile);
        assertThat(new LicenseManager(licenseFile.toURI().toURL()).getLicenses(Set.of(A_FEATURE_PACK)))
                .contains(LICENSE_ONE, LICENSE_TWO);
    }

    @Test
    public void printAcceptedLicenses() throws Exception {
        new LicenseManager().recordAgreements(List.of(LICENSE_ONE, LICENSE_TWO), serverPath);

        final Path licensesPath = licensesFolder(serverPath);
        assertThat(licensesPath.resolve("name.txt"))
                .exists()
                .hasContent(LICENSE_ONE.getText());
        assertThat(licensesPath.resolve("name2.txt"))
                .exists()
                .hasContent(LICENSE_TWO.getText());
        assertThat(readProperties(licensesPath.resolve(LICENSE_AGREEMENT_FILENAME)))
                .containsEntry("license.0.name", "name")
                .containsEntry("license.1.name", "name2")
                .containsEntry("license.0.file", "name.txt")
                .containsEntry("license.1.file", "name2.txt")
                .containsKey("license.0.timestamp")
                .containsKey("license.1.timestamp");
    }

    @Test
    public void dontGenerateAnyFilesIfLicenseListEmpty() throws Exception {
        new LicenseManager().recordAgreements(Collections.emptyList(), serverPath);

        assertThat(licensesFolder(serverPath)).doesNotExist();
    }

    @Test
    public void acceptedLicenseFilenameReplacesWhitespacesAndLowerCases() throws Exception {
        final License l1 = new License("test NAME", A_FEATURE_PACK, "title", "text");

        new LicenseManager().recordAgreements(List.of(l1), serverPath);

        System.out.println(serverPath);
        assertThat(licensesFolder(serverPath).resolve("test-name.txt"))
                .exists();
    }

    @Test
    public void addsAcceptedLicenses() throws Exception {
        new LicenseManager().recordAgreements(List.of(LICENSE_ONE), serverPath);
        new LicenseManager().recordAgreements(List.of(LICENSE_TWO), serverPath);

        final Path licensesPath = licensesFolder(serverPath);
        assertThat(licensesPath.resolve("name.txt"))
                .exists()
                .hasContent(LICENSE_ONE.getText());
        assertThat(licensesPath.resolve("name2.txt"))
                .exists()
                .hasContent(LICENSE_TWO.getText());
        assertThat(readProperties(licensesPath.resolve(LICENSE_AGREEMENT_FILENAME)))
                .containsEntry("license.0.name", "name")
                .containsEntry("license.1.name", "name2")
                .containsEntry("license.0.file", "name.txt")
                .containsEntry("license.1.file", "name2.txt")
                .containsKey("license.0.timestamp")
                .containsKey("license.1.timestamp");
    }

    private static Properties readProperties(Path resolve) throws IOException {
        final Properties acceptedProperties = new Properties();
        try (FileInputStream fis = new FileInputStream(resolve.toFile())) {
            acceptedProperties.load(fis);
        }
        return acceptedProperties;
    }

    private static Path licensesFolder(Path serverPath) {
        final Path licensesPath = serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(LICENSES_FOLDER);
        return licensesPath;
    }
}