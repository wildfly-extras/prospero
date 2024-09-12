package org.wildfly.prospero.signatures;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.util.encoders.Hex;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pgpainless.PGPainless;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.utils.TestSignatureUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PGPLocalKeystoreTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private PGPLocalKeystore localGpgKeystore;
    private Path file;

    @Before
    public void setUp() throws Exception {
        file = temp.newFolder("keyring-test-folder").toPath();
        localGpgKeystore = KeystoreManager.keystoreFor(file.resolve("store.gpg"));
    }

    @After
    public void tearDown() {
        localGpgKeystore.close();
    }

    // start of initialization tests

    @Test
    public void creatingKeyringWithoutKeyDoesntCreateFile() throws Exception {
        assertThat(file.resolve("store.gpg"))
                .doesNotExist();
    }

    // end of initialization tests

    /*
     * start of add key tests
     */
    @Test
    public void addKeyToKeyring() throws Exception {
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKey);

        assertThat(readPublicKeys())
                .map(PGPPublicKey::getFingerprint)
                .map(Hex::toHexString)
                .containsExactlyElementsOf(getFingerPrints(generatedKey));
    }

    @Test
    public void addKeyToExistingKeyring() throws Exception {
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");
        final PGPSecretKeyRing generatedKeyTwo = TestSignatureUtils.generateSecretKey("Test 2", "test");

        // add initial key
        importKeyRing(generatedKey);

        // re-create Keyring to check that no caching happens
        this.localGpgKeystore = KeystoreManager.keystoreFor(file.resolve("store.gpg"));
        // and add another key
        importKeyRing(generatedKeyTwo);

        assertThat(readPublicKeys())
                .map(PGPPublicKey::getFingerprint)
                .map(Hex::toHexString)
                .containsExactlyInAnyOrderElementsOf(getFingerPrints(generatedKey, generatedKeyTwo));
    }

    @Test
    public void addExistingKeyAgain_ThrowsException() throws Exception {
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");

        // add initial key
        importKeyRing(generatedKey);

        // try to add another key
        assertThatThrownBy(()-> importKeyRing(generatedKey))
                .isInstanceOf(DuplicatedCertificateException.class);

        assertThat(readPublicKeys())
                .map(PGPPublicKey::getFingerprint)
                .map(Hex::toHexString)
                .containsExactlyInAnyOrderElementsOf(getFingerPrints(generatedKey));
    }

    /*
     * end of add key tests
     */

    /*
     * start of remove key tests
     */
    @Test
    public void removeKeyFromKeyring() throws Exception {
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKey);

        localGpgKeystore.removeCertificate(new PGPKeyId(generatedKey.getPublicKey().getKeyID()));

        assertNull("Expected the keystore file to not be present",
                PGPainless.readKeyRing().keyRing(new FileInputStream(file.resolve("store.gpg").toFile())));
    }

    @Test
    public void removeKeyFromKeyringWithTwoKeys() throws Exception {
        final PGPSecretKeyRing generatedKey1 = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKey1);

        // and import another key
        final PGPSecretKeyRing generatedKey2 = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKey2);

        localGpgKeystore.removeCertificate(new PGPKeyId(generatedKey1.getPublicKey().getKeyID()));

        assertThat(readPublicKeys())
                .map(PGPPublicKey::getFingerprint)
                .map(Hex::toHexString)
                .containsExactlyElementsOf(getFingerPrints(generatedKey2));
    }

    @Test
    public void removeKeyFromEmptyStore_ReturnsFalse() throws Exception {
        final PGPSecretKeyRing generatedKey1 = TestSignatureUtils.generateSecretKey("Test", "test");

        assertFalse("Removing non-existing cert should return false",
                localGpgKeystore.removeCertificate(new PGPKeyId(generatedKey1.getPublicKey().getKeyID())));

        assertThat(readPublicKeys())
                .map(PGPPublicKey::getFingerprint)
                .map(Hex::toHexString)
                .isEmpty();
    }

    @Test
    public void removeNonExistingKey_ReturnsFalse() throws Exception {
        final PGPSecretKeyRing generatedKey1 = TestSignatureUtils.generateSecretKey("Test", "test");

        importKeyRing(generatedKey1);

        // and import another key
        final PGPSecretKeyRing generatedKey2 = TestSignatureUtils.generateSecretKey("Test", "test");

        assertFalse("Removing non-existing cert should return false",
                localGpgKeystore.removeCertificate(new PGPKeyId(generatedKey2.getPublicKey().getKeyID())));

        assertThat(readPublicKeys())
                .map(PGPPublicKey::getFingerprint)
                .map(Hex::toHexString)
                .containsExactlyInAnyOrderElementsOf(getFingerPrints(generatedKey1));
    }

    // TODO: remove subkey throws exception

    /*
     * end of remove key tests
     */

    /*
     * start of import certificate tests
     */
    @Test
    public void importRevocations() throws Exception {
        final File revokeFile = temp.newFile("revoke.gpg");
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");
        final PGPSignature revocationSignature = TestSignatureUtils.generateRevocationKeys(generatedKey, "test");

        importKeyRing(generatedKey);
        localGpgKeystore.revokeCertificate(revocationSignature);

        final List<PGPPublicKey> publicKeys = readPublicKeys();
        assertThat(publicKeys)
                .map(PGPPublicKey::getFingerprint)
                .map(Hex::toHexString)
                .containsExactlyInAnyOrderElementsOf(getFingerPrints(generatedKey));

        assertThat(publicKeys).allMatch(PGPLocalKeystoreTest::isRevoked);
    }

    @Test
    public void multipleKeystoresUse() throws Exception {
        final PGPSecretKeyRing generatedKey1 = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKey1);

        // and import another key
        final PGPSecretKeyRing generatedKey2 = TestSignatureUtils.generateSecretKey("Test", "test");
        try (PGPLocalKeystore localGpgKeystore2 = KeystoreManager.keystoreFor(file.resolve("store.gpg"))) {
            localGpgKeystore2.importCertificate(asList(generatedKey2.getPublicKeys()));
        }

        localGpgKeystore.removeCertificate(new PGPKeyId(generatedKey1.getPublicKey().getKeyID()));

        assertThat(readPublicKeys())
                .map(PGPPublicKey::getFingerprint)
                .map(Hex::toHexString)
                .containsExactlyElementsOf(getFingerPrints(generatedKey2));
    }

    /*
     * end of import certificate tests
     */

    /*
     * start of get certificate tests
     */

    @Test
    public void getExistingKey_ReturnCertificate() throws Exception {
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKey);

        final PGPPublicKey certificate = localGpgKeystore.getCertificate(new PGPKeyId(generatedKey.getPublicKey().getKeyID()));

        assertThat(certificate)
                .isEqualTo(generatedKey.getPublicKey());
    }

    @Test
    public void getNonExistingKey_ReturnsNull() throws Exception {
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKey);

        final PGPPublicKey certificate = localGpgKeystore.getCertificate(new PGPKeyId(123L));

        assertThat(certificate)
                .isNull();
    }

    @Test
    public void getExistingSubkey_ReturnsSubkey() throws Exception {
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKey);

        final Iterator<PGPPublicKey> publicKeys = generatedKey.getPublicKeys();
        PGPPublicKey subkey = null;
        while (publicKeys.hasNext()) {
            subkey = publicKeys.next();
            if (!subkey.isMasterKey()) {
                break;
            }
        }
        if (subkey == null) {
            Assert.fail("The generate key has no subkeys");
        }
        final PGPPublicKey certificate = localGpgKeystore.getCertificate(new PGPKeyId(subkey.getKeyID()));

        assertThat(certificate)
                .isEqualTo(subkey);
    }

    /*
     * end of get certificate tests
     */

    /*
     * start of list certificates tests
     */
    @Test
    public void listExistingKey_ReturnsKeys() throws Exception {
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKey);

        final Collection<PGPPublicKeyInfo> certificates = localGpgKeystore.listCertificates();

        assertThat(certificates)
                .containsExactlyInAnyOrderElementsOf(TestSignatureUtils.keyInfoOf(generatedKey));
    }

    @Test
    public void listMultipleExistingKeys_ReturnsKeys() throws Exception {
        final PGPSecretKeyRing generatedKeyOne = TestSignatureUtils.generateSecretKey("Test", "test");
        final PGPSecretKeyRing generatedKeyTwo = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKeyOne);
        importKeyRing(generatedKeyTwo);

        final Collection<PGPPublicKeyInfo> certificates = localGpgKeystore.listCertificates();

        final Collection<PGPPublicKeyInfo> expectedKeys = TestSignatureUtils.keyInfoOf(generatedKeyOne);
        expectedKeys.addAll(TestSignatureUtils.keyInfoOf(generatedKeyTwo));
        assertThat(certificates)
                .containsExactlyInAnyOrderElementsOf(expectedKeys);
    }

    @Test
    public void listWhenNoKeysArePresent_ReturnsEmptyList() throws Exception {
        final Collection<PGPPublicKeyInfo> certificates = localGpgKeystore.listCertificates();

        assertThat(certificates)
                .isEmpty();
    }

    /*
     * end of list certificate tests
     */

    /*
     * start of import revoke certificate tests
     */
    @Test
    public void revokeExistingCertificate() throws Exception {
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKey);
        final PGPSignature revocationSignature = TestSignatureUtils.generateRevocationKeys(generatedKey, "test");

        localGpgKeystore.revokeCertificate(revocationSignature);

        final PGPPublicKey certificate = localGpgKeystore.getCertificate(new PGPKeyId(generatedKey.getPublicKey().getKeyID()));
        assertTrue("Certificate should have been marked as revoked", certificate.hasRevocation());
    }

    @Test
    public void revokeCertificateOnEmptyKeystore() throws Exception {
        final PGPSecretKeyRing generatedKey = TestSignatureUtils.generateSecretKey("Test", "test");
        PGPSignature revocationSignature = TestSignatureUtils.generateRevocationKeys(generatedKey, "test");

        assertThatThrownBy(()->localGpgKeystore.revokeCertificate(revocationSignature))
                .isInstanceOf(NoSuchCertificateException.class)
                .hasMessageContaining(new PGPKeyId(generatedKey.getPublicKey().getKeyID()).getHexKeyID());
    }

    @Test
    public void revokeNotImportedCertificateEmptyKeystore() throws Exception {
        final PGPSecretKeyRing generatedKeyOne = TestSignatureUtils.generateSecretKey("Test", "test");
        final PGPSecretKeyRing generatedKeyTwo = TestSignatureUtils.generateSecretKey("Test", "test");
        importKeyRing(generatedKeyOne);
        PGPSignature revocationSignature = TestSignatureUtils.generateRevocationKeys(generatedKeyTwo, "test");

        assertThatThrownBy(()->localGpgKeystore.revokeCertificate(revocationSignature))
                .isInstanceOf(NoSuchCertificateException.class)
                .hasMessageContaining(new PGPKeyId(generatedKeyTwo.getPublicKey().getKeyID()).getHexKeyID());
    }

    /*
     * end of import revoke certificate tests
     */

    private List<PGPPublicKey> readPublicKeys() throws IOException {
        final List<PGPPublicKey> keyList = new ArrayList<>();
        if (!Files.exists(file.resolve("store.gpg"))) {
            return Collections.emptyList();
        }
        final PGPPublicKeyRingCollection pgpKeyRing = PGPainless.readKeyRing().publicKeyRingCollection(new FileInputStream(file.resolve("store.gpg").toFile()));
        final Iterator<PGPPublicKeyRing> keyRings = pgpKeyRing.getKeyRings();
        while (keyRings.hasNext()) {
            final PGPPublicKeyRing keyRing = keyRings.next();
            final Iterator<PGPPublicKey> publicKeys = keyRing.getPublicKeys();
            while (publicKeys.hasNext()) {
                final PGPPublicKey key = publicKeys.next();
                keyList.add(key);
            }
        }
        return keyList;
    }

    private static List<String> getFingerPrints(PGPSecretKeyRing... generatedKeys) {
        final List<String> fingerprintList = new ArrayList<>();
        for (PGPSecretKeyRing generatedKey : generatedKeys) {
            final Iterator<PGPPublicKey> publicKeys = generatedKey.getPublicKeys();
            while (publicKeys.hasNext()) {
                final PGPPublicKey key = publicKeys.next();
                fingerprintList.add(Hex.toHexString(key.getFingerprint()));
            }
        }
        return fingerprintList;
    }

    private static boolean isRevoked(PGPPublicKey key) {
        // only check master keys not subkeys
        return !key.isMasterKey() || key.hasRevocation();

    }

    private void importKeyRing(PGPSecretKeyRing generatedKey) throws IOException, OperationException {
        final File keyFile = temp.newFile();
        TestSignatureUtils.exportPublicKeys(generatedKey, keyFile);
        localGpgKeystore.importCertificate(asList(generatedKey.getPublicKeys()));
    }

    private <T> List<T> asList(Iterator<T> publicKeys) {
        final ArrayList<T> res = new ArrayList<>();
        while (publicKeys.hasNext()) {
            res.add(publicKeys.next());
        }
        return res;
    }
}