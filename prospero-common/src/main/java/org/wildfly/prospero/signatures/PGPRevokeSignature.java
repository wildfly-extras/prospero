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
import org.bouncycastle.bcpg.BCPGInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPSignature;
import org.wildfly.prospero.ProsperoLogger;

/**
 * Contains a revoke signature
 */
public class PGPRevokeSignature {
    private final PGPSignature pgpSignature;

    public PGPRevokeSignature(File revokeKey) throws InvalidCertificateException {
        try (FileInputStream fis = new FileInputStream(revokeKey)) {
            pgpSignature = new PGPSignature(new BCPGInputStream(new ArmoredInputStream(fis)));
        } catch (IOException | PGPException e) {
            throw ProsperoLogger.ROOT_LOGGER.invalidCertificate(revokeKey.getAbsolutePath(), e.getMessage(), e);
        }
    }

    public PGPRevokeSignature(String location, InputStream inputStream) throws InvalidCertificateException {
        try {
            pgpSignature = new PGPSignature(new BCPGInputStream(new ArmoredInputStream(inputStream)));
        } catch (IOException | PGPException e) {
            throw ProsperoLogger.ROOT_LOGGER.invalidCertificate(location, e.getMessage(), e);
        }
    }

    public PGPKeyId getRevokedKeyId() {
        return new PGPKeyId(pgpSignature.getKeyID());
    }

    public PGPSignature getPgpSignature() {
        return pgpSignature;
    }
}
