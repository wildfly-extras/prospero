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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfirmingKeystoreAdapterTest {

    @Mock
    private PGPLocalKeystore wrappedKeystore;
    @Mock
    private Function<String, Boolean> acceptor;
    private ConfirmingKeystoreAdapter confirmingKeystoreWrapper;

    @Before
    public void setUp() throws Exception {
        confirmingKeystoreWrapper = new ConfirmingKeystoreAdapter(wrappedKeystore, acceptor);
    }

    @Test
    public void getCallsWrappedKeystore() throws Exception {
        final PGPPublicKey mockedKey = mockPublicKey();
        when(wrappedKeystore.getCertificate(new PGPKeyId("a_key"))).thenReturn(mockedKey);

        final PGPPublicKey res = confirmingKeystoreWrapper.get("a_key");

        Mockito.verify(wrappedKeystore).getCertificate(new PGPKeyId("a_key"));
        assertThat(res)
                .isEqualTo(mockedKey);
    }

    @Test
    public void addCallsWrappedKeystoreIfAcceptorReturnsTrue() throws Exception {
        final List<PGPPublicKey> mockedKeys = List.of(mockPublicKey());
        final ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);

        when(acceptor.apply(descCaptor.capture())).thenReturn(true);
        assertTrue(confirmingKeystoreWrapper.add(mockedKeys));

        Mockito.verify(wrappedKeystore).importCertificate(mockedKeys);
        assertThat(descCaptor.getValue())
                .contains("Test User", "abcd");
    }

    @Test
    public void addDoesNotCallsWrappedKeystoreIfAcceptorReturnsFalse() throws Exception {
        final List<PGPPublicKey> mockedKeys = List.of(mockPublicKey());
        final ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);

        when(acceptor.apply(descCaptor.capture())).thenReturn(false);
        assertFalse(confirmingKeystoreWrapper.add(mockedKeys));

        Mockito.verify(wrappedKeystore, Mockito.never()).importCertificate(mockedKeys);
        assertThat(descCaptor.getValue())
                .contains("Test User", "abcd");
    }

    /*
     * Need to set some fields so that we can generate a description
     */
    private static PGPPublicKey mockPublicKey() {
        final PGPPublicKey key = Mockito.mock(PGPPublicKey.class);
        when(key.getUserIDs()).thenReturn(List.of("Test User").iterator());
        when(key.getFingerprint()).thenReturn(Hex.decode("abcd"));
        return key;
    }
}