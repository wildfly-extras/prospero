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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KeystoreManagerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void getKeystoreForTheSamePathTwice_ReturnsSameElement() throws Exception {
        final Path keystorePath = temp.newFile("test.crt").toPath();
        final PGPLocalKeystore keystoreOne = KeystoreManager.keystoreFor(keystorePath);
        final PGPLocalKeystore keystoreTwo = KeystoreManager.keystoreFor(keystorePath);

        assertThat(unwrap(keystoreOne)).isSameAs(unwrap(keystoreTwo));
    }

    @Test
    public void getKeystoreForTheDifferentPath_ReturnsDifferentElement() throws Exception {
        final Path keystorePathOne = temp.newFile("test-one.crt").toPath();
        final Path keystorePathTwo = temp.newFile("test-two.crt").toPath();
        final PGPLocalKeystore keystoreOne = KeystoreManager.keystoreFor(keystorePathOne);
        final PGPLocalKeystore keystoreTwo = KeystoreManager.keystoreFor(keystorePathTwo);

        assertThat(unwrap(keystoreOne)).isNotSameAs(unwrap(keystoreTwo));
    }

    @Test
    public void getKeystoreAfterItWasClosed_ReturnsDifferentElement() throws Exception {
        final Path keystorePath = temp.newFile("test.crt").toPath();
        final PGPLocalKeystore keystoreOne = KeystoreManager.keystoreFor(keystorePath);

        keystoreOne.close();
        final PGPLocalKeystore keystoreTwo = KeystoreManager.keystoreFor(keystorePath);

        assertThat(unwrap(keystoreOne)).isNotSameAs(unwrap(keystoreTwo));
    }

    @Test
    public void closingKeystoreSecondTimeIsNoop() throws Exception {
        final Path keystorePath = temp.newFile("test.crt").toPath();
        final PGPLocalKeystore keystoreOne = KeystoreManager.keystoreFor(keystorePath);

        keystoreOne.close();
        keystoreOne.close();
        final PGPLocalKeystore keystoreTwo = KeystoreManager.keystoreFor(keystorePath);

        assertThat(unwrap(keystoreOne)).isNotSameAs(unwrap(keystoreTwo));
    }

    @Test
    public void closingKeystoreDoesntRemoveItIfItIsStillUsed() throws Exception {
        final Path keystorePath = temp.newFile("test.crt").toPath();
        final PGPLocalKeystore keystoreOne = KeystoreManager.keystoreFor(keystorePath);
        final PGPLocalKeystore keystoreTwo = KeystoreManager.keystoreFor(keystorePath);

        keystoreOne.close();
        final PGPLocalKeystore keystoreThree = KeystoreManager.keystoreFor(keystorePath);

        assertThat(unwrap(keystoreThree)).isSameAs(unwrap(keystoreTwo));
    }

    private static CachedPGPKeystore unwrap(PGPLocalKeystore keystoreThree) {
        // very ugly, use only for testing
        return (CachedPGPKeystore) ((KeystoreManager.KeystoreWrapper)keystoreThree).getWrapped();
    }
}