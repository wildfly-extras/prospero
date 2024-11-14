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

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;

public class VerificationResult {
    private final List<InvalidBinary> invalidBinaries;
    private final List<Path> modifiedFiles;
    private final Set<PGPPublicKeyInfo> trustedCertificates;

    public VerificationResult(List<InvalidBinary> invalidBinaries, List<Path> modifiedFiles, Set<PGPPublicKeyInfo> trustedCertificates) {
        this.invalidBinaries = invalidBinaries;
        this.modifiedFiles = modifiedFiles;
        this.trustedCertificates = trustedCertificates;
    }

    public Collection<InvalidBinary> getUnsignedBinary() {
        return invalidBinaries;
    }

    public Collection<Path> getModifiedFiles() {
        return modifiedFiles;
    }

    public Collection<PGPPublicKeyInfo> getTrustedCertificates() {
        return trustedCertificates;
    }

    public static class InvalidBinary {
        private final Path path;
        private final String gav;
        private final SignatureResult.Result error;
        private final String keyId;

        public InvalidBinary(Path path, String gav, SignatureResult.Result error) {
            this(path, gav, error, null);
        }

        public InvalidBinary(Path path, String gav, SignatureResult.Result error, String keyId) {
            this.path = path;
            this.gav = gav;
            this.error = error;
            this.keyId = keyId;
        }

        public Path getPath() {
            return path;
        }

        public String getGav() {
            return gav;
        }

        public SignatureResult.Result getError() {
            return error;
        }

        public String getKeyId() {
            return keyId;
        }

        @Override
        public String toString() {
            return "InvalidBinary{" +
                    "path=" + path +
                    ", gav='" + gav + '\'' +
                    ", error=" + error +
                    ", keyId='" + keyId + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InvalidBinary that = (InvalidBinary) o;
            return Objects.equals(path, that.path) && Objects.equals(gav, that.gav) && error == that.error && Objects.equals(keyId, that.keyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, gav, error, keyId);
        }
    }
}
