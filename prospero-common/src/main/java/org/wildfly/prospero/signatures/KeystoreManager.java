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

package org.wildfly.prospero.signatures;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.wildfly.prospero.api.exceptions.MetadataException;

/**
 * Creates and destroys keystores used to access trusted certificates. Any access to the keystore should be done through
 * this manager.
 */
public final class KeystoreManager {

    private static final Map<Path, CachedPGPKeystore> keyringCache = new HashMap<>();
    private static final Map<Path, List<KeystoreWrapper>> keyringsInUse = new HashMap<>();

    /**
     * Retrieves a keystore associated with this {@code keyStoreFile}. If one doesn't exist yet, it is created.
     *
     * @param keyStoreFile
     * @return
     * @throws MetadataException - if unable to read the keystore file
     */
    public static synchronized PGPLocalKeystore keystoreFor(Path keyStoreFile) throws MetadataException {
        if (!keyringCache.containsKey(keyStoreFile)) {
            keyringCache.put(keyStoreFile, new CachedPGPKeystore(keyStoreFile));
        }
        final CachedPGPKeystore keystore = keyringCache.get(keyStoreFile);

        if (!keyringsInUse.containsKey(keyStoreFile)) {
            keyringsInUse.put(keyStoreFile, new ArrayList<>());
        }
        final KeystoreWrapper keystoreWrapper = new KeystoreWrapper(keyStoreFile, keystore);
        keyringsInUse.get(keyStoreFile).add(keystoreWrapper);

        return keystoreWrapper;
    }

    /**
     * Removes the keystore from the cache if it is no longer used.
     *
     * @param keystore
     */
    static synchronized void keystoreClosed(KeystoreWrapper keystore) {
        final List<KeystoreWrapper> usedKeystores = keyringsInUse.get(keystore.getKeyStoreFile());
        if (usedKeystores != null) {
            usedKeystores.remove(keystore);

            if (usedKeystores.isEmpty()) {
                keyringsInUse.remove(keystore.getKeyStoreFile());
                keyringCache.remove(keystore.getKeyStoreFile());
            }
        }
    }

    /**
     * Works together with KeystoreManager to make sure the keystores are closed correctly
     */
    static class KeystoreWrapper implements PGPLocalKeystore {

        private final PGPLocalKeystore wrapped;
        private final Path keyStoreFile;

        private KeystoreWrapper(Path keyStoreFile, PGPLocalKeystore wrapped) {
            this.wrapped = wrapped;
            this.keyStoreFile = keyStoreFile;
        }

        PGPLocalKeystore getWrapped() {
            return wrapped;
        }

        /*
         * remove the instance from manager
         */
        @Override
        public synchronized void close() {
            keystoreClosed(this);
            wrapped.close();
        }

        @Override
        public boolean removeCertificate(PGPKeyId keyId) throws KeystoreWriteException {
            return wrapped.removeCertificate(keyId);
        }

        @Override
        public void revokeCertificate(PGPSignature pgpSignature) throws KeystoreWriteException, NoSuchCertificateException {
            wrapped.revokeCertificate(pgpSignature);
        }

        @Override
        public void importCertificate(List<PGPPublicKey> pgpPublicKeys) throws DuplicatedCertificateException, KeystoreWriteException {
            wrapped.importCertificate(pgpPublicKeys);
        }

        @Override
        public PGPPublicKey getCertificate(PGPKeyId keyIdHex) {
            return wrapped.getCertificate(keyIdHex);
        }

        @Override
        public Collection<PGPPublicKeyInfo> listCertificates() {
            return wrapped.listCertificates();
        }

        // internal methods used to create the keystores
        private Path getKeyStoreFile() {
            return keyStoreFile;
        }
    }
}
