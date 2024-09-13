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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.eclipse.jgit.util.Hex;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.exceptions.MetadataException;

/**
 * Represents a store containing trusted public keys used to verify components
 */
class CachedPGPKeystore implements PGPLocalKeystore {

    private final Path keyStoreFile;
    private PGPPublicKeyRingCollection publicKeyRingCollection;

    /**
     * Should only be created thrown {@code KeystoreManager}
     *
     * @param keyStoreFile
     */
    CachedPGPKeystore(Path keyStoreFile) throws MetadataException {
        this.keyStoreFile = keyStoreFile;
        if (Files.exists(keyStoreFile)) {
            try {
                publicKeyRingCollection = new PGPPublicKeyRingCollection(
                        new FileInputStream(keyStoreFile.toFile()),
                        new JcaKeyFingerprintCalculator());
            } catch (IOException | PGPException e) {
                throw ProsperoLogger.ROOT_LOGGER.unableToReadKeyring(keyStoreFile, e.getLocalizedMessage(), e);
            }
        }
    }

    @Override
    public void close() {
        // no-op
    }

    private synchronized PGPPublicKeyRingCollection getPublicKeyRingCollection() {
        if (publicKeyRingCollection == null) {
            publicKeyRingCollection = new PGPPublicKeyRingCollection(Collections.emptyList());
        }
        return publicKeyRingCollection;
    }

    @Override
    public synchronized boolean removeCertificate(PGPKeyId keyId) throws KeystoreWriteException {
        final Iterator<PGPPublicKeyRing> keyRings = getPublicKeyRingCollection().getKeyRings();
        while (keyRings.hasNext()) {
            final PGPPublicKeyRing keyRing = keyRings.next();
            final Iterator<PGPPublicKey> publicKeys = keyRing.getPublicKeys();
            while (publicKeys.hasNext()) {
                final PGPPublicKey next = publicKeys.next();
                if (next.getKeyID() == keyId.getKeyID()) {
                    this.publicKeyRingCollection = PGPPublicKeyRingCollection.removePublicKeyRing(publicKeyRingCollection, keyRing);

                    try(FileOutputStream outStream = new FileOutputStream(keyStoreFile.toFile())) {
                        getPublicKeyRingCollection().encode(outStream);
                    } catch (IOException e) {
                        throw ProsperoLogger.ROOT_LOGGER.unableToWriteKeystore(keyStoreFile, e.getLocalizedMessage(), e);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public synchronized void revokeCertificate(PGPSignature pgpSignature) throws NoSuchCertificateException, KeystoreWriteException {
        final long keyId = pgpSignature.getKeyID();

        final PGPPublicKeyRingCollection publicKeyRingCollection = getPublicKeyRingCollection();
        final Iterator<PGPPublicKeyRing> keyRings = publicKeyRingCollection.getKeyRings();
        PGPPublicKeyRing keyRing = null;
        PGPPublicKey publicKey = null;
        while (keyRings.hasNext()) {
            keyRing = keyRings.next();
            publicKey = keyRing.getPublicKey(keyId);
            if (publicKey != null) {
                break;
            }
        }

        if (publicKey == null) {
            throw ProsperoLogger.ROOT_LOGGER.noSuchCertificate(new PGPKeyId(keyId).getHexKeyID());
        }


        final PGPPublicKey pgpPublicKey = PGPPublicKey.addCertification(publicKey, pgpSignature);
        PGPPublicKeyRing newKeyRing = PGPPublicKeyRing.insertPublicKey(keyRing, pgpPublicKey);

        PGPPublicKeyRingCollection collection = PGPPublicKeyRingCollection.removePublicKeyRing(publicKeyRingCollection, keyRing);
        collection = PGPPublicKeyRingCollection.addPublicKeyRing(collection, newKeyRing);

        this.publicKeyRingCollection = collection;
        try(FileOutputStream outStream = new FileOutputStream(keyStoreFile.toFile())) {
            getPublicKeyRingCollection().encode(outStream);
        } catch (IOException e) {
            throw ProsperoLogger.ROOT_LOGGER.unableToWriteKeystore(keyStoreFile, e.getLocalizedMessage(), e);
        }

    }

    @Override
    public synchronized void importCertificate(List<PGPPublicKey> pgpPublicKeys) throws DuplicatedCertificateException, KeystoreWriteException {
        final PGPPublicKeyRing pgpPublicKeyRing = new PGPPublicKeyRing(pgpPublicKeys);
        if (getKey(pgpPublicKeyRing.getPublicKey().getKeyID()) != null) {
            throw ProsperoLogger.ROOT_LOGGER.certificateAlreadyExists(new PGPKeyId(pgpPublicKeyRing.getPublicKey().getKeyID()).getHexKeyID());
        }
        publicKeyRingCollection = PGPPublicKeyRingCollection.addPublicKeyRing(getPublicKeyRingCollection(), pgpPublicKeyRing);
        try(FileOutputStream outStream = new FileOutputStream(keyStoreFile.toFile())) {
            getPublicKeyRingCollection().encode(outStream);
        } catch (IOException e) {
            throw ProsperoLogger.ROOT_LOGGER.unableToWriteKeystore(keyStoreFile, e.getLocalizedMessage(), e);
        }
    }

    @Override
    public synchronized PGPPublicKey getCertificate(PGPKeyId keyId) {
        return getKey(keyId.getKeyID());
    }

    private synchronized PGPPublicKey getKey(long keyID) {
        final Iterator<PGPPublicKeyRing> keyRings = getPublicKeyRingCollection().getKeyRings();
        while (keyRings.hasNext()) {
            final PGPPublicKeyRing keyRing = keyRings.next();
            final PGPPublicKey publicKey = keyRing.getPublicKey(keyID);
            if (publicKey != null) {
                return publicKey;
            }
        }
        return null;
    }

    @Override
    public synchronized Collection<PGPPublicKeyInfo> listCertificates() {
        final Iterator<PGPPublicKeyRing> keyRings = getPublicKeyRingCollection().getKeyRings();
        final ArrayList<PGPPublicKeyInfo> keyInfos = new ArrayList<>();
        while (keyRings.hasNext()) {
            final PGPPublicKeyRing keyRing = keyRings.next();
            final Iterator<PGPPublicKey> publicKeys = keyRing.getPublicKeys();
            while (publicKeys.hasNext()) {
                final PGPPublicKey key = publicKeys.next();
                final PGPKeyId keyID = new PGPKeyId(key.getKeyID());
                final String fingerprint = Hex.toHexString(key.getFingerprint()).toUpperCase(Locale.ROOT);
                final Iterator<String> userIDs = key.getUserIDs();
                final ArrayList<String> tmpUserIds = new ArrayList<>();
                while (userIDs.hasNext()) {
                    tmpUserIds.add(userIDs.next());
                }
                final List<String> identities = Collections.unmodifiableList(tmpUserIds);
                final LocalDateTime creationDate = key.getCreationTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                final LocalDateTime expiryDate = key.getValidSeconds() == 0?null:creationDate.plusSeconds(key.getValidSeconds());
                final PGPPublicKeyInfo.Status status;
                if (key.hasRevocation()) {
                    status = PGPPublicKeyInfo.Status.REVOKED;
                } else if (expiryDate != null && expiryDate.isBefore(LocalDateTime.now())) {
                    status = PGPPublicKeyInfo.Status.EXPIRED;
                } else {
                    status = PGPPublicKeyInfo.Status.TRUSTED;
                }
                keyInfos.add(new PGPPublicKeyInfo(keyID, status, fingerprint, identities, creationDate, expiryDate));
            }
        }

        return keyInfos;
    }
}
