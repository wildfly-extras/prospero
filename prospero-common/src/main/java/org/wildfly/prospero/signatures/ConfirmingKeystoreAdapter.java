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

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.wildfly.channel.gpg.GpgKeystore;
import org.wildfly.channel.gpg.KeystoreOperationException;

/**
 * Verifies that the public key is accepted by the user before adding it. Uses a console acceptor to interact with the user.
 */
public class ConfirmingKeystoreAdapter implements GpgKeystore {

    private final PGPLocalKeystore localGpgKeystore;
    private final Function<String, Boolean> acceptor;

    public ConfirmingKeystoreAdapter(PGPLocalKeystore localGpgKeystore, Function<String, Boolean> acceptor) {
        Objects.requireNonNull(localGpgKeystore);
        Objects.requireNonNull(acceptor);

        this.localGpgKeystore = localGpgKeystore;
        this.acceptor = acceptor;
    }

    @Override
    public PGPPublicKey get(String keyID) {
        return localGpgKeystore.getCertificate(new PGPKeyId(keyID));
    }

    @Override
    public boolean add(List<PGPPublicKey> publicKey) throws KeystoreOperationException {
        final String description = describeImportedKeys(publicKey);
        if (acceptor.apply(description)) {
            try {
                localGpgKeystore.importCertificate(publicKey);
            } catch (DuplicatedCertificateException | KeystoreWriteException e) {
                throw new KeystoreOperationException(e.getMessage(), e);
            }
            return true;
        } else {
            return false;
        }
    }

    private static String describeImportedKeys(List<PGPPublicKey> pgpPublicKeys) {
        final StringBuilder sb = new StringBuilder();
        for (PGPPublicKey pgpPublicKey : pgpPublicKeys) {
            final Iterator<String> userIDs = pgpPublicKey.getUserIDs();
            while (userIDs.hasNext()) {
                sb.append(userIDs.next());
            }
            sb.append(": ").append(org.bouncycastle.util.encoders.Hex.toHexString(pgpPublicKey.getFingerprint()));
        }
        return sb.toString();
    }
}
