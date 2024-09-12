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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.eclipse.jgit.util.Hex;
import org.wildfly.prospero.ProsperoLogger;

/**
 * Represents information contained in a public key
 */
public class PGPPublicKeyInfo {

    /**
     * parses a certificate from a file. The certificate is expected to be armour-encoded.
     *
     * @param file
     * @return
     * @throws InvalidCertificateException - if the certificate cannot be parsed
     */
    public static PGPPublicKeyInfo parse(File file) throws InvalidCertificateException {
        final PGPPublicKeyRing pgpPublicKeys;
        try {
            pgpPublicKeys = new PGPPublicKeyRing(new ArmoredInputStream(new FileInputStream(file)), new JcaKeyFingerprintCalculator());
        } catch (IOException e) {
            throw ProsperoLogger.ROOT_LOGGER.invalidCertificate(file.getAbsolutePath(), e.getLocalizedMessage(), e);
        }
        final PGPPublicKey key = pgpPublicKeys.getPublicKey();
        final Iterator<String> userIDs = key.getUserIDs();
        final ArrayList<String> tmpUserIds = new ArrayList<>();
        while (userIDs.hasNext()) {
            tmpUserIds.add(userIDs.next());
        }

        PGPKeyId keyID = new PGPKeyId(key.getKeyID());
        String fingerprint = Hex.toHexString(key.getFingerprint()).toUpperCase(Locale.ROOT);
        List<String> identity = Collections.unmodifiableList(tmpUserIds);
        Status status = key.hasRevocation() ? PGPPublicKeyInfo.Status.REVOKED : PGPPublicKeyInfo.Status.TRUSTED;
        LocalDateTime issueDate = key.getCreationTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime expiryDate = key.getValidSeconds() == 0?null:issueDate.plusSeconds(key.getValidSeconds());

        return new PGPPublicKeyInfo(keyID, status, fingerprint, identity, issueDate, expiryDate);
    }

    private final PGPKeyId keyID;

    public enum Status {TRUSTED, EXPIRED, REVOKED}

    private final Status status;
    private final String fingerprint;
    private final List<String> identity;
    private final LocalDateTime issueDate;
    private final LocalDateTime expiryDate;

    public PGPPublicKeyInfo(PGPKeyId keyID, Status status, String fingerprint, List<String> identity, LocalDateTime issueDate, LocalDateTime expiryDate) {
        this.keyID = keyID;
        this.status = status;
        this.fingerprint = fingerprint;
        this.identity = identity;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
    }

    public PGPKeyId getKeyID() {
        return keyID;
    }

    public Status getStatus() {
        return status;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Collection<String> getIdentity() {
        return identity;
    }

    public LocalDateTime getIssueDate() {
        return issueDate;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    @Override
    public String toString() {
        return "KeyInfo{" +
                "keyID='" + keyID + '\'' +
                ", status=" + status +
                ", fingerprint='" + fingerprint + '\'' +
                ", identity=" + identity +
                ", issueDate=" + issueDate +
                ", expiryDate=" + expiryDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PGPPublicKeyInfo keyInfo = (PGPPublicKeyInfo) o;
        return Objects.equals(keyID, keyInfo.keyID) && status == keyInfo.status && Objects.equals(fingerprint, keyInfo.fingerprint) && Objects.equals(identity, keyInfo.identity) && Objects.equals(issueDate, keyInfo.issueDate) && Objects.equals(expiryDate, keyInfo.expiryDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyID, status, fingerprint, identity, issueDate, expiryDate);
    }
}
