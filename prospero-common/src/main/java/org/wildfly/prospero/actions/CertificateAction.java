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

package org.wildfly.prospero.actions;

import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.wildfly.prospero.signatures.InvalidCertificateException;
import org.wildfly.prospero.signatures.KeystoreWriteException;
import org.wildfly.prospero.signatures.DuplicatedCertificateException;
import org.wildfly.prospero.signatures.PGPKeyId;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.signatures.PGPRevokeSignature;
import org.wildfly.prospero.signatures.PGPPublicKey;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.signatures.NoSuchCertificateException;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.signatures.KeystoreManager;
import org.wildfly.prospero.signatures.PGPLocalKeystore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Operations to manage the trusted certificates used to verify server artifacts
 */
public class CertificateAction implements AutoCloseable {
    private final PGPLocalKeystore localGpgKeystore;

    public CertificateAction(Path installationDir) throws MetadataException {
        final Path keyringPath = installationDir.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg");
        localGpgKeystore = KeystoreManager.keystoreFor(keyringPath);
    }

    /**
     * Adds the {@code trustCertificate} to the stored trusted certificates used to verify components.
     *
     * @param trustCertificate - the certificate to import.
     * @throws InvalidCertificateException - if the {@code trustCertificate} cannot be parsed as a public key
     * @throws DuplicatedCertificateException - if a certificate with the same ID as {@code trustCertificate} is already imported
     * @throws KeystoreWriteException - if unable to persist changes to the keystore
     */
    public void importCertificate(PGPPublicKey trustCertificate)
            throws InvalidCertificateException, DuplicatedCertificateException, KeystoreWriteException {

        final PGPPublicKeyRing pgpPublicKeyRing = trustCertificate.getPublicKeyRing();

        localGpgKeystore.importCertificate(asList(pgpPublicKeyRing.getPublicKeys()));
    }

    /**
     * Removes a public key with ID {@code keyID} from the keystore
     *
     * @param keyID - the HEX form of the public key ID
     * @throws NoSuchCertificateException - if the keystore does not contain a matching public key
     * @throws KeystoreWriteException - if unable to persist changes to the keystore
     */
    public void removeCertificate(PGPKeyId keyID) throws NoSuchCertificateException, KeystoreWriteException {
        if (localGpgKeystore.getCertificate(keyID) == null) {
            throw ProsperoLogger.ROOT_LOGGER.noSuchCertificate(keyID.getHexKeyID());
        }
        localGpgKeystore.removeCertificate(keyID);
    }

    /**
     * Imports a revocation signature for one of the public keys. This public key will no longer be trusted to verify artifacts.
     *
     * @param revokeCertificate - the revocation signature for one of the imported public keys
     *
     * @throws NoSuchCertificateException - if the public key for the revocation signature has not been imported yet
     * @throws KeystoreWriteException - if unable to persist changes to the keystore
     */
    public void revokeCertificate(PGPRevokeSignature revokeCertificate)
            throws NoSuchCertificateException, KeystoreWriteException {
        final PGPSignature pgpSignature = revokeCertificate.getPgpSignature();
        localGpgKeystore.revokeCertificate(pgpSignature);
    }

    /**
     * List all public keys imported in the server.
     *
     * @return a Collection of {@code KeyInfo}s
     */
    public Collection<PGPPublicKeyInfo> listCertificates() {
        return localGpgKeystore.listCertificates();
    }

    /**
     * Retrieves a public key with ID of {@code keyID} from the server's keystore.
     *
     * @param keyID - a HEX encoded ID of the public key
     * @return the {@code KeyInfo} of the public key,
     *         or null if no matching public key was found
     */
    public PGPPublicKeyInfo getCertificate(PGPKeyId keyID) {
        final Optional<PGPPublicKeyInfo> pgpPublicKey = localGpgKeystore.listCertificates().stream()
                .filter(k->k.getKeyID().equals(keyID))
                .findFirst();
        return pgpPublicKey.orElse(null);
    }

    private <T> List<T> asList(Iterator<T> publicKeys) {
        final ArrayList<T> res = new ArrayList<>();
        while (publicKeys.hasNext()) {
            res.add(publicKeys.next());
        }
        return res;
    }

    @Override
    public void close() throws Exception {
        localGpgKeystore.close();
    }
}
