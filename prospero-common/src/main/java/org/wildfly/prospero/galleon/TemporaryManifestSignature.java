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

package org.wildfly.prospero.galleon;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.signatures.KeystoreManager;
import org.wildfly.prospero.signatures.KeystoreWriteException;
import org.wildfly.prospero.signatures.PGPKeyId;
import org.wildfly.prospero.signatures.PGPLocalKeystore;

/**
 * Generates and holds a temporary signature of a manifest used during revert.
 * The manifest needs to be signed if any of the channels requires validation.
 */
class TemporaryManifestSignature implements AutoCloseable {

    private final String KEY_IDENTITY = "Installation Manager Restore Certificate";;
    private static final int SIG_HASH = HashAlgorithmTags.SHA512;
    private static final int[] HASH_PREFERENCES = new int[]{
    HashAlgorithmTags.SHA512, HashAlgorithmTags.SHA384, HashAlgorithmTags.SHA256, HashAlgorithmTags.SHA224
};
    private static final int[] SYM_PREFERENCES = new int[]{
    SymmetricKeyAlgorithmTags.AES_256, SymmetricKeyAlgorithmTags.AES_192, SymmetricKeyAlgorithmTags.AES_128
};
    private static final int[] COMP_PREFERENCES = new int[]{
    CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.BZIP2, CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.UNCOMPRESSED
};
    private final Path keystoreLocation;
    private final PGPSecretKeyRing pgpSecretKey;
    private final ArrayList<File> signatures = new ArrayList<>();

    public TemporaryManifestSignature(Path keystoreLocation) throws OperationException {
        this.keystoreLocation = keystoreLocation;

        try (PGPLocalKeystore tmpKeystore = KeystoreManager.keystoreFor(keystoreLocation)) {
            this.pgpSecretKey = generateSecretKeyRing();
            tmpKeystore.importCertificate(List.of(pgpSecretKey.getPublicKey()));
        } catch (PGPException | NoSuchAlgorithmException e) {
            throw new OperationException("Unable to generate temporary key: " + e.getLocalizedMessage(), e);
        }
    }

    public void sign(File source, File signature) throws PGPException, IOException {
        signFile(source, signature);
        signatures.add(signature);
    }

    @Override
    public void close() {
        try (PGPLocalKeystore tmpKeystore = KeystoreManager.keystoreFor(keystoreLocation)) {
            tmpKeystore.removeCertificate(new PGPKeyId(pgpSecretKey.getPublicKey().getKeyID()));
        } catch (MetadataException | KeystoreWriteException e) {
            throw new RuntimeException("Unable to remove temporary public key: " + e.getLocalizedMessage(), e);
        }

        // delete generated signature files
        signatures.forEach(FileUtils::deleteQuietly);
        // remove all signatures
        signatures.removeIf((s)->true);

    }

    private void signFile(File in, File signatureFile) throws PGPException, IOException {
        final JcaPGPContentSignerBuilder contentSignerBuilder = new JcaPGPContentSignerBuilder(
                pgpSecretKey.getPublicKey().getAlgorithm(), PGPUtil.SHA256);
        PGPSignatureGenerator sGen = new PGPSignatureGenerator(contentSignerBuilder);
        sGen.init(PGPSignature.PRIMARYKEY_BINDING, pgpSecretKey.getSecretKey().extractPrivateKey(null));
        try (FileInputStream fileInputStream = new FileInputStream(in)) {
            sGen.update(fileInputStream.readAllBytes());
        }
        final PGPSignature signature = sGen.generate();
        try (FileOutputStream outStream = new FileOutputStream(signatureFile)) {
            signature.encode(outStream);
        }

    }

    private PGPSecretKeyRing generateSecretKeyRing()
            throws PGPException, NoSuchAlgorithmException {
        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");

        PGPContentSignerBuilder contentSignerBuilder = new JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, SIG_HASH);//.setProvider("BC");
        PBESecretKeyEncryptor secretKeyEncryptor = null;

        Date now = new Date();

        kpg.initialize(3072);
        KeyPair primaryKP = kpg.generateKeyPair();
        PGPKeyPair primaryKey = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, primaryKP, now);
        PGPSignatureSubpacketGenerator primarySubpackets = new PGPSignatureSubpacketGenerator();
        primarySubpackets.setKeyFlags(true, KeyFlags.CERTIFY_OTHER);
        primarySubpackets.setPreferredHashAlgorithms(false, HASH_PREFERENCES);
        primarySubpackets.setPreferredSymmetricAlgorithms(false, SYM_PREFERENCES);
        primarySubpackets.setPreferredCompressionAlgorithms(false, COMP_PREFERENCES);
        primarySubpackets.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);
        primarySubpackets.setKeyFlags(true, KeyFlags.SIGN_DATA);
        primarySubpackets.setIssuerFingerprint(false, primaryKey.getPublicKey());

        PGPKeyRingGenerator gen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, primaryKey, KEY_IDENTITY,
                sha1Calc, primarySubpackets.generate(), null, contentSignerBuilder, secretKeyEncryptor);

        return gen.generateSecretKeyRing();
    }
}
