package org.wildfly.prospero.utils;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.Streams;
import org.pgpainless.PGPainless;
import org.pgpainless.encryption_signing.EncryptionStream;
import org.pgpainless.encryption_signing.ProducerOptions;
import org.pgpainless.encryption_signing.SigningOptions;
import org.pgpainless.key.SubkeyIdentifier;
import org.pgpainless.key.protection.SecretKeyRingProtector;
import org.pgpainless.sop.SOPImpl;
import org.pgpainless.util.Passphrase;
import org.wildfly.prospero.signatures.PGPKeyId;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import sop.SOP;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TestSignatureUtils {

    /**
     * Generate key ring with private/public key pair
     *
     * @return
     * @throws Exception
     * @param userId
     * @param password
     */
    public static PGPSecretKeyRing generateSecretKey(String userId, String password) throws Exception {
        return PGPainless.generateKeyRing().modernKeyRing(userId, password);
    }

    /**
     * Sign {@code originalFile} using private key found in the {@code keyRing}. The detached signature is stored
     * next to {@code originalFile} with ".asc" suffix.
     *
     * @param keyRing
     * @param originalFile
     * @param pass
     * @return
     * @throws Exception
     */
    public static Long signFile(PGPSecretKeyRing keyRing, Path originalFile, String pass) throws Exception {
        EncryptionStream encryptionStream = null;
        try {
            encryptionStream = getEncryptionStreamWithSigning(keyRing, pass);
            try (InputStream fIn = new FileInputStream(originalFile.toFile())) {
                Streams.pipeAll(fIn, encryptionStream);
            }
        } finally {
            // can't use try-with-resources - the encryptionStream has to be close before next step, but we still need access to it
            if (encryptionStream != null) {
                encryptionStream.close();
            }
        }

        final Path signatureFilePath = originalFile.getParent().resolve(originalFile.getFileName().toString() + ".asc");

        try(FileOutputStream fos = new FileOutputStream(signatureFilePath.toFile());
            ArmoredOutputStream aos = new ArmoredOutputStream(fos)) {
            for (SubkeyIdentifier subkeyIdentifier : encryptionStream.getResult().getDetachedSignatures().keySet()) {
                final Set<PGPSignature> pgpSignatures = encryptionStream.getResult().getDetachedSignatures().get(subkeyIdentifier);
                for (PGPSignature pgpSignature : pgpSignatures) {
                    pgpSignature.encode(aos);
                    return pgpSignature.getKeyID();
                }
            }
        }
        return null;
    }

    private static EncryptionStream getEncryptionStreamWithSigning(PGPSecretKeyRing keyRing, String pass) throws PGPException, IOException {
        return PGPainless.encryptAndOrSign()
                .onOutputStream(new ByteArrayOutputStream())
                .withOptions(ProducerOptions.sign(SigningOptions.get()
                        .addDetachedSignature(
                                SecretKeyRingProtector.unlockAnyKeyWith(Passphrase.fromPassword(pass)),
                                keyRing)));
    }

    public static void exportPublicKeys(PGPSecretKeyRing pgpSecretKey, File targetFile) throws IOException {
        final List<PGPPublicKey> pubKeyList = new ArrayList<>();
        final Iterator<PGPPublicKey> publicKeys = pgpSecretKey.getPublicKeys();
        publicKeys.forEachRemaining(pubKeyList::add);
        final PGPPublicKeyRing pubKeyRing = new PGPPublicKeyRing(pubKeyList);
        try (OutputStream outStream = new ArmoredOutputStream(new FileOutputStream(targetFile))) {
            pubKeyRing.encode(outStream, true);
        }
    }

    public static Long exportRevocationKeys(PGPSecretKeyRing pgpSecretKey, File targetFile, String password) throws IOException {
        final SOP sop = new SOPImpl();
        final PGPPublicKeyRing pgpPublicKeys = PGPainless.readKeyRing().publicKeyRing(sop.revokeKey()
                .withKeyPassword(password)
                .keys(pgpSecretKey.getEncoded()).getInputStream());

        final Iterator<PGPSignature> signatures = pgpPublicKeys.getPublicKey().getSignaturesOfType(PGPSignature.KEY_REVOCATION);
        while(signatures.hasNext()) {
            final PGPSignature signature = signatures.next();
            try (ArmoredOutputStream outStream = new ArmoredOutputStream(new FileOutputStream(targetFile))) {
                signature.encode(outStream, true);
                return signature.getKeyID();
            }
        }
        return null;
    }

    public static PGPSignature generateRevocationKeys(PGPSecretKeyRing pgpSecretKey, String password) throws IOException {
        final SOP sop = new SOPImpl();
        final PGPPublicKeyRing pgpPublicKeys = PGPainless.readKeyRing().publicKeyRing(sop.revokeKey()
                .withKeyPassword(password)
                .keys(pgpSecretKey.getEncoded()).getInputStream());

        final Iterator<PGPSignature> signatures = pgpPublicKeys.getPublicKey().getSignaturesOfType(PGPSignature.KEY_REVOCATION);
        while(signatures.hasNext()) {
            return signatures.next();
        }
        return null;
    }

    public static Collection<PGPPublicKeyInfo> keyInfoOf(PGPSecretKeyRing secretKey) {
        final ArrayList<PGPPublicKeyInfo> res = new ArrayList<>();
        final Iterator<PGPPublicKey> publicKeys = secretKey.getPublicKeys();
        while (publicKeys.hasNext()) {
            final PGPPublicKey key = publicKeys.next();
            res.add(keyInfoOf(PGPPublicKeyInfo.Status.TRUSTED, key));
        }
        return res;
    }

    public static PGPPublicKeyInfo keyInfoOf(PGPPublicKeyInfo.Status status, PGPPublicKey publicKey) {
        final Iterator<String> userIDs = publicKey.getUserIDs();
        final ArrayList<String> userIDsArray = new ArrayList<>();
        while (userIDs.hasNext()) {
            userIDsArray.add(userIDs.next());
        }
        final LocalDateTime creationDate = publicKey.getCreationTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        final LocalDateTime expiryDate = publicKey.getValidSeconds() > 0 ? creationDate.plusSeconds(publicKey.getValidSeconds()) : null;
        return new PGPPublicKeyInfo(new PGPKeyId(publicKey.getKeyID()), status,
                Hex.toHexString(publicKey.getFingerprint()).toUpperCase(Locale.ROOT), userIDsArray,
                creationDate, expiryDate
        );
    }
}
