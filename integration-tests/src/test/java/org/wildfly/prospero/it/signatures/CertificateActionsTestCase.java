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

package org.wildfly.prospero.it.signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.util.encoders.Hex;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.signatures.DuplicatedCertificateException;
import org.wildfly.prospero.signatures.PGPKeyId;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.actions.CertificateAction;
import org.wildfly.prospero.signatures.PGPRevokeSignature;
import org.wildfly.prospero.signatures.PGPPublicKey;
import org.wildfly.prospero.signatures.InvalidCertificateException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.signatures.NoSuchCertificateException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.test.CertificateUtils;

public class CertificateActionsTestCase {

    @ClassRule
    public static TemporaryFolder classTemp = new TemporaryFolder();
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private Path serverPath;
    private static PGPSecretKeyRing pgpSecretKeysOne;
    private static PGPSecretKeyRing pgpSecretKeysTwo;
    private static File pubCertOne;
    private static File pubCertTwo;
    private CertificateAction certificateAction;
    private Path keyringPath;
    private static File revocationCrtOne;

    @BeforeClass
    public static void classSetUp() throws Exception {
        // generate the keys once to speed up the tests
        pgpSecretKeysOne = CertificateUtils.generatePrivateKey();
        pgpSecretKeysTwo = CertificateUtils.generatePrivateKey();
        pubCertOne = CertificateUtils.exportPublicCertificate(pgpSecretKeysOne, classTemp.getRoot().toPath().resolve("pub-one.crt").toFile());
        pubCertTwo = CertificateUtils.exportPublicCertificate(pgpSecretKeysTwo, classTemp.getRoot().toPath().resolve("pub-two.crt").toFile());
        revocationCrtOne = CertificateUtils.generateRevocationSignature(pgpSecretKeysOne, classTemp.newFile("revoke.crt"));
    }

    @Before
    public void setUp() throws Exception {
        serverPath = mockServer();
        certificateAction = new CertificateAction(serverPath);

        keyringPath = serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg");
    }

    private Path mockServer() throws IOException {
        final Path serverPath = temp.newFolder("server").toPath();
        Files.createDirectory(serverPath.resolve(ProsperoMetadataUtils.METADATA_DIR));
        return serverPath;
    }

    @After
    public void tearDown() throws Exception {
        certificateAction.close();
    }

    // add certificate
    //   add new certificate - no keystore - success
    @Test
    public void addNewCertificateNoKeystore_PresentInKeystore() throws Exception {
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));

        CertificateUtils.assertKeystoreContainsOnly(keyringPath, keyID(pgpSecretKeysOne));
    }

    //   add new certificate - existing keystore - success
    @Test
    public void addNewCertificateToExistingKeystore_PresentInKeystore() throws Exception {
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));
        certificateAction.importCertificate(new PGPPublicKey(pubCertTwo));

        CertificateUtils.assertKeystoreContainsOnly(keyringPath,
                keyID(pgpSecretKeysOne),
                keyID(pgpSecretKeysTwo)
        );
    }

    //   add an invalid certificate - error
    @Test
    public void addInvalidCertificateToKeystore_ThrowsException() throws Exception {
        final File invalidCert = Files.writeString(temp.getRoot().toPath().resolve("invalid-cert.crt"),
                "i'm not a cert").toFile();

        assertThatThrownBy(() -> certificateAction.importCertificate(new PGPPublicKey(invalidCert)))
                .isInstanceOf(InvalidCertificateException.class)
                .hasMessageContaining(ProsperoLogger.ROOT_LOGGER.invalidCertificate(invalidCert.getAbsolutePath(),  "", null).getMessage());

        if (Files.exists(keyringPath)) {
            CertificateUtils.assertKeystoreIsEmpty(keyringPath);
        }
    }

    //   add an already added certificate - error
    @Test
    public void addExistingCertificateToKeystore_ThrowsException() throws Exception {
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));

        final long existingKeyID = keyID(pgpSecretKeysOne);
        assertThatThrownBy(() -> certificateAction.importCertificate(new PGPPublicKey(pubCertOne)))
                .isInstanceOf(DuplicatedCertificateException.class)
                .hasMessageContaining(ProsperoLogger.ROOT_LOGGER.certificateAlreadyExists(
                        new PGPKeyId(existingKeyID).getHexKeyID()).getMessage());

        CertificateUtils.assertKeystoreContainsOnly(keyringPath, existingKeyID);
    }

    //   add a certificate to non-writable keystore - error

    @Test
    public void addCertificateToBrokenKeystore_ThrowsException() throws Exception {
        try {
            Files.createFile(keyringPath);
            assertTrue("Unable to mark keyring as read-only", keyringPath.toFile().setReadOnly());
            assertThatThrownBy(() -> certificateAction.importCertificate(new PGPPublicKey(pubCertOne)))
                    .isInstanceOf(OperationException.class)
                    .hasMessageContaining(ProsperoLogger.ROOT_LOGGER.unableToWriteKeystore(keyringPath, "", null).getMessage())
                    .hasCauseInstanceOf(IOException.class);
        } finally {
            keyringPath.toFile().setWritable(true);
        }

    }
    // list certificate
    //   when no certificates

    @Test
    public void listCertificatesEmptyKeystore_EmptyList() throws Exception {
        // import and remove should result in an empty keyring file
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));
        long keyID = keyID(pgpSecretKeysOne);
        certificateAction.removeCertificate(new PGPKeyId(keyID));

        final Collection<PGPPublicKeyInfo> keys = certificateAction.listCertificates();

        assertThat(keys)
                .isEmpty();
    }
    //   when no keystore

    @Test
    public void listCertificatesNoKeystore_EmptyList() throws Exception {
        final Collection<PGPPublicKeyInfo> keys = certificateAction.listCertificates();

        assertThat(keys)
                .isEmpty();
    }

    //   when one certificate
    @Test
    public void listCertificates() throws Exception {
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));
        certificateAction.importCertificate(new PGPPublicKey(pubCertTwo));

        final Collection<PGPPublicKeyInfo> keys = certificateAction.listCertificates();

        assertThat(keys)
                .containsExactlyInAnyOrder(
                        keyInfoOf(pgpSecretKeysOne),
                        keyInfoOf(pgpSecretKeysTwo)
                );
    }

    //   when certificate is revoked
    @Test
    public void listRevokedCertificate() throws Exception {
        final File revokeKey = CertificateUtils.generateRevokedKey(pgpSecretKeysOne, temp.newFile("revoke.crt"));
        certificateAction.importCertificate(new PGPPublicKey(revokeKey));

        final Collection<PGPPublicKeyInfo> keys = certificateAction.listCertificates();

        assertThat(keys)
                .containsExactlyInAnyOrder(
                        keyInfoOf(pgpSecretKeysOne, PGPPublicKeyInfo.Status.REVOKED)
                );
    }

    //   when certificate is expired
    @Test
    public void listExpiredCertificate() throws Exception {
        final PGPSecretKeyRing expiredKey = CertificateUtils.generateExpiredPrivateKey();
        final File expiredKeyCert = CertificateUtils.exportPublicCertificate(expiredKey, temp.newFile("expired.cert"));
        certificateAction.importCertificate(new PGPPublicKey(expiredKeyCert));

        CertificateUtils.waitUntilExpires(expiredKey);

        final Collection<PGPPublicKeyInfo> keys = certificateAction.listCertificates();

        assertThat(keys)
                .containsExactlyInAnyOrder(
                        keyInfoOf(expiredKey, PGPPublicKeyInfo.Status.EXPIRED)
                );
    }

    // remove certificate
    //   when matching certificate - success
    @Test
    public void removeOnlyCertificatePresentInKeystore() throws Exception {
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));

        long keyID = keyID(pgpSecretKeysOne);
        certificateAction.removeCertificate(new PGPKeyId(keyID));

        CertificateUtils.assertKeystoreIsEmpty(keyringPath);
    }

    //   when no certificates - error
    @Test
    public void removeWhenNoCertificatesArePresentInKeystore() throws Exception {
        // import and remove should result in an empty keyring file
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));
        long keyID2 = keyID(pgpSecretKeysOne);
        certificateAction.removeCertificate(new PGPKeyId(keyID2));

        long keyID = keyID(pgpSecretKeysOne);
        assertThatThrownBy(()-> {
            long keyID1 = keyID(pgpSecretKeysOne);
            certificateAction.removeCertificate(new PGPKeyId(keyID1));
        })
                .isInstanceOf(NoSuchCertificateException.class)
                .hasMessageContaining(ProsperoLogger.ROOT_LOGGER.noSuchCertificate(new PGPKeyId(keyID).getHexKeyID()).getMessage());

        CertificateUtils.assertKeystoreIsEmpty(keyringPath);
    }

    //   when no keystore - error
    @Test
    public void removeWhenKeystoreDoesntExist() throws Exception {
        long keyID = keyID(pgpSecretKeysOne);
        assertThatThrownBy(()-> {
            long keyID1 = keyID(pgpSecretKeysOne);
            certificateAction.removeCertificate(new PGPKeyId(keyID1));
        })
                .isInstanceOf(NoSuchCertificateException.class)
                .hasMessageContaining(ProsperoLogger.ROOT_LOGGER.noSuchCertificate(new PGPKeyId(keyID).getHexKeyID()).getMessage());

        assertThat(keyringPath)
                .doesNotExist();
    }

    //   when no matching certificates - error
    @Test
    public void removeWhenTheCertificateIsNotPresentInKeystore() throws Exception {
        // import certOne and try to remote certTwo
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));

        long keyID = keyID(pgpSecretKeysTwo);
        assertThatThrownBy(()-> {
            long keyID1 = keyID(pgpSecretKeysTwo);
            certificateAction.removeCertificate(new PGPKeyId(keyID1));
        })
                .isInstanceOf(NoSuchCertificateException.class)
                .hasMessageContaining(ProsperoLogger.ROOT_LOGGER.noSuchCertificate(new PGPKeyId(keyID).getHexKeyID()).getMessage());

        CertificateUtils.assertKeystoreContains(keyringPath, keyID(pgpSecretKeysOne));
    }

    // revoke certificate
    //   when matching certificate - success
    @Test
    public void revokeCertificateMarksItRevoked() throws Exception {
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));

        certificateAction.revokeCertificate(new PGPRevokeSignature(revocationCrtOne));

        final Collection<PGPPublicKeyInfo> keys = certificateAction.listCertificates();
        assertThat(keys)
                .containsExactlyInAnyOrder(
                        keyInfoOf(pgpSecretKeysOne, PGPPublicKeyInfo.Status.REVOKED)
                );
    }

    //   when no certificates - error
    @Test
    public void revokeCertificateWhenKeystoreIsEmpty_NoSuchCertificateError() throws Exception {
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));
        long keyID1 = keyID(pgpSecretKeysOne);
        certificateAction.removeCertificate(new PGPKeyId(keyID1));

        long keyID = keyID(pgpSecretKeysOne);
        assertThatThrownBy(()->certificateAction.revokeCertificate(new PGPRevokeSignature(revocationCrtOne)))
                .isInstanceOf(NoSuchCertificateException.class)
                .hasMessageContaining(ProsperoLogger.ROOT_LOGGER.noSuchCertificate(new PGPKeyId(keyID).getHexKeyID()).getMessage());
    }

    //   when no matching certificates - error
    @Test
    public void revokeCertificateWhenCertificateIsNotPresent_NoSuchCertificateError() throws Exception {
        certificateAction.importCertificate(new PGPPublicKey(pubCertTwo));

        long keyID = keyID(pgpSecretKeysOne);
        assertThatThrownBy(()->certificateAction.revokeCertificate(new PGPRevokeSignature(revocationCrtOne)))
                .isInstanceOf(NoSuchCertificateException.class)
                .hasMessageContaining(ProsperoLogger.ROOT_LOGGER.noSuchCertificate(new PGPKeyId(keyID).getHexKeyID()).getMessage());

        CertificateUtils.assertKeystoreContainsOnly(keyringPath, keyID(pgpSecretKeysTwo));
    }

    //   when the certificate is invalid
    @Test
    public void revokingWithInvalidCertificate_ThrowsException() throws Exception {
        File revocationSignature = temp.newFile("revocation.sig");
        Files.writeString(revocationSignature.toPath(), "I'm not a certificate");

        assertThatThrownBy(()->certificateAction.revokeCertificate(new PGPRevokeSignature(revocationSignature)))
                .isInstanceOf(InvalidCertificateException.class);
    }

    @Test
    public void createActionWithInvalidKeystoreFile_ThrowsException() throws Exception {
        Files.writeString(keyringPath, "I'm not a kerying collection");

        // need to close current certificateAction to remove cached keystore
        certificateAction.close();
        assertThatThrownBy(() -> new CertificateAction(serverPath).close())
                .isInstanceOf(MetadataException.class)
                .hasMessageContaining(ProsperoLogger.ROOT_LOGGER.unableToReadKeyring(keyringPath, "", null).getMessage());
    }

    // get certificate
    //  get from non-existing keyring - return null
    @Test
    public void getCertificateEmptyKeystore_ReturnsNull() throws Exception {
        long keyID = pgpSecretKeysOne.getPublicKey().getKeyID();
        assertThat(certificateAction.getCertificate(new PGPKeyId(keyID)))
                .isNull();
    }

    //  get non-existing certificate - return null
    @Test
    public void getNonExistingCertificate_ReturnsNull() throws Exception {
        certificateAction.importCertificate(new PGPPublicKey(pubCertTwo));
        long keyID = pgpSecretKeysOne.getPublicKey().getKeyID();
        assertThat(certificateAction.getCertificate(new PGPKeyId(keyID)))
                .isNull();
    }

    //  get an existing certificate - return cert
    @Test
    public void getExistingCertificate_ReturnsKeyInfo() throws Exception {
        certificateAction.importCertificate(new PGPPublicKey(pubCertOne));
        long keyID = pgpSecretKeysOne.getPublicKey().getKeyID();
        assertThat(certificateAction.getCertificate(new PGPKeyId(keyID)))
                .isEqualTo(keyInfoOf(pgpSecretKeysOne, PGPPublicKeyInfo.Status.TRUSTED));
    }

    private static long keyID(PGPSecretKeyRing pgpSecretKeysOne) {
        return pgpSecretKeysOne.getPublicKey().getKeyID();
    }

    private static PGPPublicKeyInfo keyInfoOf(PGPSecretKeyRing key) {
        return keyInfoOf(key, PGPPublicKeyInfo.Status.TRUSTED);
    }

    private static PGPPublicKeyInfo keyInfoOf(PGPSecretKeyRing key, PGPPublicKeyInfo.Status status) {
        final Iterator<String> userIDs = key.getPublicKey().getUserIDs();
        final ArrayList<String> userIDsArray = new ArrayList<>();
        while (userIDs.hasNext()) {
            userIDsArray.add(userIDs.next());
        }
        final LocalDateTime creationDate = key.getPublicKey().getCreationTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        long keyID = keyID(key);
        return new PGPPublicKeyInfo(new PGPKeyId(keyID), status,
                Hex.toHexString(key.getPublicKey().getFingerprint()).toUpperCase(Locale.ROOT), userIDsArray,
                creationDate, creationDate.plusSeconds(key.getPublicKey().getValidSeconds())
        );
    }
}
