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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.wildfly.prospero.ProsperoLogger;

public class PGPPublicKey {
    private final String location;
    private final PGPPublicKeyRing publicKeyRing;

    public PGPPublicKey(String location, InputStream inputStream) throws InvalidCertificateException {
        this.location = location;

        try {
            this.publicKeyRing = new PGPPublicKeyRing(new ArmoredInputStream(inputStream), new JcaKeyFingerprintCalculator());
        } catch (IOException e) {
            throw ProsperoLogger.ROOT_LOGGER.invalidCertificate(location, e.getLocalizedMessage(), e);
        }
    }

    public PGPPublicKey(File certFile) throws InvalidCertificateException {
        this.location = certFile.toPath().toAbsolutePath().toString();
        try (FileInputStream inputStream = new FileInputStream(certFile)) {
            this.publicKeyRing = new PGPPublicKeyRing(new ArmoredInputStream(inputStream), new JcaKeyFingerprintCalculator());
        } catch (IOException e) {
            throw ProsperoLogger.ROOT_LOGGER.invalidCertificate(location, e.getLocalizedMessage(), e);
        }
    }

    public String getLocation() {
        return location;
    }

    public PGPPublicKeyRing getPublicKeyRing() {
        return publicKeyRing;
    }
}
