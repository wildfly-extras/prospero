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

package org.wildfly.prospero.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.api.Condition;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.util.io.Streams;
import org.pgpainless.PGPainless;
import org.pgpainless.algorithm.KeyFlag;
import org.pgpainless.encryption_signing.EncryptionStream;
import org.pgpainless.encryption_signing.ProducerOptions;
import org.pgpainless.encryption_signing.SigningOptions;
import org.pgpainless.key.SubkeyIdentifier;
import org.pgpainless.key.generation.KeySpec;
import org.pgpainless.key.generation.type.KeyType;
import org.pgpainless.key.generation.type.rsa.RsaLength;
import org.pgpainless.key.protection.UnprotectedKeysProtector;
import org.pgpainless.key.util.RevocationAttributes;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.prospero.signatures.PGPKeyId;

public class CertificateUtils {

    public static PGPSecretKeyRing generatePrivateKey() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, PGPException {
        return PGPainless.generateKeyRing().simpleRsaKeyRing("Test <test@test.org>", RsaLength._4096);
    }

    public static PGPSecretKeyRing generateExpiredPrivateKey() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, PGPException {
        // for some reason sometimes it generates a non-expiring cert
        PGPSecretKeyRing expiredPrivateKey = null;
        int regenCounter = 0;
        do {
            if (regenCounter++ > 10) {
                throw new RuntimeException("Unable to generate expired certificate");
            }
            try {
                expiredPrivateKey = doGenereteExpiredPrivateKey();
            } catch (IllegalArgumentException e) {
                // sometimes the exception is thrown when setting the expiry date, ignore it and retry
                e.printStackTrace();
            }
        } while (expiredPrivateKey == null || expiredPrivateKey.getPublicKey().getValidSeconds() <= 0);
        return expiredPrivateKey;
    }

    private static PGPSecretKeyRing doGenereteExpiredPrivateKey() throws NoSuchAlgorithmException, PGPException, InvalidAlgorithmParameterException {
        return PGPainless.buildKeyRing()
                .setPrimaryKey(KeySpec.getBuilder(KeyType.RSA(RsaLength._4096), KeyFlag.CERTIFY_OTHER, KeyFlag.SIGN_DATA, KeyFlag.ENCRYPT_COMMS))
                .addUserId("Test <test@test.org>")
                .setExpirationDate(new Date(System.currentTimeMillis() + 2_000))
                .build();
    }

    public static void assertKeystoreContainsOnly(Path keystoreFile, long... expectedKeyIds) throws IOException {
        final HashSet<PGPKeyId> actualKeyIds = getKeyIds(keystoreFile);

        assertThat(actualKeyIds)
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(expectedKeyIds).boxed()
                        .map(PGPKeyId::new)
                        .collect(Collectors.toList()));
    }

    public static void assertKeystoreContains(Path keystoreFile, long keyID) throws IOException {
        final HashSet<PGPKeyId> keyIds = getKeyIds(keystoreFile);

        assertThat(keyIds).contains(new PGPKeyId(keyID));
    }

    public static void assertKeystoreIsEmpty(Path keystoreFile) throws IOException {
        final HashSet<PGPKeyId> keyIds = getKeyIds(keystoreFile);

        assertThat(keyIds).isEmpty();
    }

    private static HashSet<PGPKeyId> getKeyIds(Path keystoreFile) throws IOException {
        final PGPPublicKeyRingCollection pgpPublicKeys = PGPainless.readKeyRing().publicKeyRingCollection(new FileInputStream(keystoreFile.toFile()));

        final HashSet<PGPKeyId> keyIds = new HashSet<>();
        final Iterator<PGPPublicKeyRing> keyRings = pgpPublicKeys.getKeyRings();
        while (keyRings.hasNext()) {
            final Iterator<PGPPublicKey> publicKeys = keyRings.next().getPublicKeys();
            while (publicKeys.hasNext()) {
                keyIds.add(new PGPKeyId(publicKeys.next().getKeyID()));
            }
        }
        return keyIds;
    }

    public static File exportPublicCertificate(PGPSecretKeyRing keyRing, File publicCertFile) throws IOException {
        // export the public certificate
        try (ArmoredOutputStream outStream = new ArmoredOutputStream(new FileOutputStream(publicCertFile))) {
            keyRing.getPublicKey().encode(outStream);
        }
        return publicCertFile;
    }

    public static File generateRevocationSignature(PGPSecretKeyRing pgpValidKeys, File publicCertFile) throws PGPException, IOException {
        final PGPSecretKeyRing revokedKeyRing = PGPainless.modifyKeyRing(pgpValidKeys)
                .revoke(new UnprotectedKeysProtector(),
                        RevocationAttributes
                                .createKeyRevocation()
                                .withReason(RevocationAttributes.Reason.KEY_COMPROMISED)
                                .withDescription("The key is revoked"))
                .done();
        final Iterator<PGPSignature> signatures = revokedKeyRing.getPublicKey().getSignatures();
        while (signatures.hasNext()) {
            final PGPSignature signature = signatures.next();
            if (signature.getSignatureType() == PGPSignature.KEY_REVOCATION) {
                try (ArmoredOutputStream outStream = new ArmoredOutputStream(new FileOutputStream(publicCertFile))) {
                    signature.encode(outStream);
                }
            }
        }

        return publicCertFile;
    }

    public static File generateRevokedKey(PGPSecretKeyRing pgpValidKeys, File publicCertFile) throws PGPException, IOException {
        final PGPSecretKeyRing revokedKeyRing = PGPainless.modifyKeyRing(pgpValidKeys)
                .revoke(new UnprotectedKeysProtector(),
                        RevocationAttributes
                                .createKeyRevocation()
                                .withReason(RevocationAttributes.Reason.KEY_COMPROMISED)
                                .withDescription("The key is revoked"))
                .done();
        return exportPublicCertificate(revokedKeyRing, publicCertFile);
    }

    public static File signFile(Path file, File signatureFile, PGPSecretKeyRing pgpSecretKeys) throws PGPException, IOException {
        final SigningOptions signOptions = SigningOptions.get()
                .addDetachedSignature(new UnprotectedKeysProtector(), pgpSecretKeys);

        final EncryptionStream encryptionStream = PGPainless.encryptAndOrSign()
                .onOutputStream(new FileOutputStream(signatureFile))
                .withOptions(ProducerOptions.sign(signOptions));

        Streams.pipeAll(new FileInputStream(file.toFile()), encryptionStream); // pipe the data through
        encryptionStream.close();

        // wrap signature in armour
        try(FileOutputStream fos = new FileOutputStream(signatureFile);
            final ArmoredOutputStream aos = new ArmoredOutputStream(fos)) {
            for (SubkeyIdentifier subkeyIdentifier : encryptionStream.getResult().getDetachedSignatures().keySet()) {
                final Set<PGPSignature> pgpSignatures = encryptionStream.getResult().getDetachedSignatures().get(subkeyIdentifier);
                for (PGPSignature pgpSignature : pgpSignatures) {
                    pgpSignature.encode(aos);
                }
            }
        }
        return signatureFile;
    }

    public static Condition<Throwable> result(SignatureValidator.SignatureException exception, SignatureResult.Result expectedResult) {
        return new Condition<>(e -> exception.getSignatureResult().getResult() == expectedResult,
                "Expected exception state %s but was %s", expectedResult, exception.getSignatureResult().getResult());
    }

    public static boolean isExpired(PGPPublicKey publicKey) {
        if (publicKey.getValidSeconds() == 0) {
            System.out.println(publicKey.getValidSeconds());
            return false;
        } else {
            final Instant expiry = Instant.from(publicKey.getCreationTime().toInstant().plus(publicKey.getValidSeconds(), ChronoUnit.SECONDS));
            return expiry.isBefore(Instant.now());
        }
    }

    public static void waitUntilExpires(PGPSecretKeyRing expiredKeys) throws InterruptedException {
        final long start = System.currentTimeMillis();
        final long maxWait = 60_000;
        while (!CertificateUtils.isExpired(expiredKeys.getPublicKey())) {
            if (System.currentTimeMillis() > start + maxWait) {
                throw new RuntimeException(String.format("The certificate %s has not expired in %d seconds",
                        new PGPKeyId(expiredKeys.getPublicKey().getKeyID()).getHexKeyID(), maxWait));
            }
            //noinspection BusyWait
            Thread.sleep(100);
        }
    }
}
