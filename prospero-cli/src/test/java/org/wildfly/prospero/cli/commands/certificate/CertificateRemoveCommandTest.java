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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.signatures.PGPKeyId;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.actions.CertificateAction;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.test.CertificateUtils;
import org.wildfly.prospero.test.MetadataTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class CertificateRemoveCommandTest extends AbstractConsoleTest {

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
        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.REMOVE,
                CliConstants.KEY_ID, "idontexist");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(Paths.get(".").toAbsolutePath().toAbsolutePath())
                .getMessage()));
    }

    @Test
    public void keyIdOrRevokeCertificateIsRequired() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.REMOVE,
                CliConstants.DIR, installationDir.toString());

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains("Missing required argument", CliConstants.KEY_ID, CliConstants.REVOKE_CERTIFICATE);
    }

    @Test
    public void noCertificateWithKeyId() throws Exception {
        when(certificateAction.getCertificate(new PGPKeyId("idontexist"))).thenReturn(null);

        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.REMOVE,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.KEY_ID, "idontexist");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains(CliMessages.MESSAGES.noSuchCertificate("idontexist"));
    }

    @Test
    public void revokeCertificateIsNonExisting() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.REMOVE,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.REVOKE_CERTIFICATE, "idontexist");

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains(CliMessages.MESSAGES.nonExistingFilePath(Path.of("idontexist")).getMessage());
    }

    @Test
    public void callRemoveWithTheKeyId() throws Exception {
        when(certificateAction.getCertificate(new PGPKeyId("a_key"))).thenReturn(new PGPPublicKeyInfo(new PGPKeyId("A"), PGPPublicKeyInfo.Status.TRUSTED,
                "", Collections.emptyList(), null, null));

        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.REMOVE,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.KEY_ID, "a_key");

        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(certificateAction).removeCertificate(new PGPKeyId("a_key"));
    }

    @Test
    public void callRevokeWithCertFile() throws Exception {
        final PGPSecretKeyRing pgpSecretKeys = CertificateUtils.generatePrivateKey();
        final File file = CertificateUtils.generateRevocationSignature(pgpSecretKeys, tempFolder.newFile("revoke.crt"));
        when(certificateAction.getCertificate(new PGPKeyId(pgpSecretKeys.getPublicKey().getKeyID()))).thenReturn(new PGPPublicKeyInfo(new PGPKeyId("A"), PGPPublicKeyInfo.Status.TRUSTED,
                "", Collections.emptyList(), null, null));

        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.REMOVE,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.REVOKE_CERTIFICATE, file.getAbsolutePath());

        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);
        verify(certificateAction).revokeCertificate(any());
    }

    @Test
    public void callRevokeWithCertFileNonExistingPublicKey() throws Exception {
        final PGPSecretKeyRing pgpSecretKeys = CertificateUtils.generatePrivateKey();
        final File file = CertificateUtils.generateRevocationSignature(pgpSecretKeys, tempFolder.newFile("revoke.crt"));
        final PGPKeyId keyID = new PGPKeyId(pgpSecretKeys.getPublicKey().getKeyID());
        when(certificateAction.getCertificate(keyID))
                .thenReturn(null);

        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.REMOVE,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.REVOKE_CERTIFICATE, file.getAbsolutePath());

        Assert.assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains(CliMessages.MESSAGES.noSuchCertificate(keyID.getHexKeyID()));
    }

    @Test
    public void invalidRevokeCertificate() throws Exception {
        final File file = Files.writeString(tempFolder.newFile("revoke.crt").toPath(), "I'm not a certificate").toFile();

        int exitCode = commandLine.execute(CliConstants.Commands.CERTIFICATE, CliConstants.Commands.REMOVE,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.REVOKE_CERTIFICATE, file.getAbsolutePath());

        Assert.assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
        assertThat(getErrorOutput())
                .contains(ProsperoLogger.ROOT_LOGGER.invalidCertificate(file.getAbsolutePath(), "", null).getMessage());
    }




}