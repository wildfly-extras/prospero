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

package org.wildfly.prospero.cli.commands.certificate;

import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import org.wildfly.prospero.cli.CliMessages;

import java.io.PrintStream;

class KeyPrinter {

    private final PrintStream writer;

    KeyPrinter(PrintStream writer) {
        this.writer = writer;
    }

    void print(PGPPublicKeyInfo key) {
        printField(CliMessages.MESSAGES.publicKeyIdLabel(), key.getKeyID().getHexKeyID());
        printField(CliMessages.MESSAGES.publicKeyFingerprintLabel(), key.getFingerprint());
        printField(CliMessages.MESSAGES.publicKeyTrustStatusLabel(), key.getStatus());
        if (!key.getIdentity().isEmpty()) {
            printField(CliMessages.MESSAGES.publicKeyUserIdsLabel(), "");
            for (String userId : key.getIdentity()) {
                writer.println(" * " + userId);
            }
        }
        printField(CliMessages.MESSAGES.publicKeyCreateTimeLabel(), key.getIssueDate());
        if (key.getExpiryDate() != null) {
            printField(CliMessages.MESSAGES.publicKeyExpiresTimeLabel(), key.getExpiryDate());
        }
    }

    private void printField(String key, Object value) {
        writer.println(key + ": " + value);
    }
}
