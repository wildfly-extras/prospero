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

import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;

public class PGPKeyId {

    private final String keyID;

    public PGPKeyId(String keyID) {
        this.keyID = keyID.toUpperCase(Locale.ROOT);
    }

    public PGPKeyId(Long keyID) {
        this.keyID = Long.toHexString(keyID).toUpperCase(Locale.ROOT);
    }

    public String getHexKeyID() {
        return keyID;
    }

    public long getKeyID() {
        // note have to use BigInteger, Long.parse produces negative long values
        return new BigInteger(keyID, 16).longValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PGPKeyId pgpKeyId = (PGPKeyId) o;
        return Objects.equals(keyID, pgpKeyId.keyID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyID);
    }

    @Override
    public String toString() {
        return "PGPKeyId{" +
                "keyID='" + keyID + '\'' +
                '}';
    }
}
