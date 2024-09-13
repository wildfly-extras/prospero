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

package org.wildfly.prospero.cli.commands.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.signatures.PGPKeyId;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import org.wildfly.prospero.actions.CertificateAction;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.test.MetadataTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class CertificateListCommandTest extends AbstractConsoleTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    @Mock
    public ActionFactory actionFactory;
    @Mock
    public CertificateAction certificateAction;

    private Path installationDir;

    protected ActionFactory createActionFactory() {
        return actionFactory;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        installationDir = tempFolder.newFolder().toPath();

        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir, "org.wildfly.core:core-feature-pack");

        when(actionFactory.certificateAction(eq(installationDir)))
                .thenReturn(certificateAction);
    }

    @Test
    public void currentDirNotValidInstallation() {
        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.LIST);

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput()).contains(CliMessages.MESSAGES.invalidInstallationDir(Paths.get(".").toAbsolutePath().toAbsolutePath())
                .getMessage());
    }

    @Test
    public void printInformationIfNoCertsAvailable() throws Exception {
        when(certificateAction.listCertificates())
                .thenReturn(Collections.emptyList());

        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.LIST,
                CliConstants.DIR, installationDir.toString());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        assertThat(getStandardOutput())
                .contains(CliMessages.MESSAGES.noPublicKeysHeader());
    }

    @Test
    public void printKeysIfCertsPresent() throws Exception {
        when(certificateAction.listCertificates())
                .thenReturn(List.of(
                        new PGPPublicKeyInfo(new PGPKeyId("A"), PGPPublicKeyInfo.Status.TRUSTED, "", Collections.emptyList(), null, null),
                        new PGPPublicKeyInfo(new PGPKeyId("B"), PGPPublicKeyInfo.Status.TRUSTED, "", Collections.emptyList(), null, null)
                ));

        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.LIST,
                CliConstants.DIR, installationDir.toString());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        assertThat(getStandardOutput())
                .contains(CliMessages.MESSAGES.publicKeysListHeader(), "Key ID: A", "Key ID: B");
    }
}