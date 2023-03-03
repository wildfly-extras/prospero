/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.galleon;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.jboss.galleon.progresstracking.DefaultProgressTracker;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.ProvisioningProgressEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class DownloadsCallbackAdapterTest {

    @Mock
    private Console console;

    @Mock
    private RepositorySystemSession session;

    @Captor
    private ArgumentCaptor<ProvisioningProgressEvent> progressEventCaptor;
    private DownloadsCallbackAdapter adapter;
    private DefaultProgressTracker<MavenArtifact> tracker;

    @Before
    public void setup() {
        adapter = new DownloadsCallbackAdapter(console);
        tracker = new DefaultProgressTracker<>(adapter);
    }

    @Test
    public void updateWhenArtifactIsResolved() throws Exception {
        tracker.starting(100);

        ProvisioningProgressEvent event = getProgressEvent();
        assertEquals(ProvisioningProgressEvent.EventType.STARTING, event.getEventType());
        assertEquals(100, event.getTotal());

        adapter.transferSucceeded(new TransferEvent.Builder(session, new TransferResource(null, null, "test.jar", null, null)).build());

        event = getProgressEvent();
        assertEquals(ProvisioningProgressEvent.EventType.UPDATE, event.getEventType());
        assertEquals(100, event.getTotal());
        assertEquals(1, event.getCompleted());
        assertEquals("test.jar", event.getCurrentItem());

        tracker.complete();
        event = getProgressEvent();

        assertEquals(ProvisioningProgressEvent.EventType.COMPLETED, event.getEventType());
    }

    @Test
    public void noUpdateBeforeStart() throws Exception {
        adapter.transferSucceeded(new TransferEvent.Builder(session, new TransferResource(null, null, "test.jar", null, null)).build());

        Mockito.verify(console, Mockito.never()).progressUpdate(progressEventCaptor.capture());
    }

    @Test
    public void noUpdateAfterComplete() throws Exception {
        tracker.starting(100);
        tracker.complete();

        adapter.transferSucceeded(new TransferEvent.Builder(session, new TransferResource(null, null, "test.jar", null, null)).build());

        Mockito.verify(console, Mockito.times(2)).progressUpdate(progressEventCaptor.capture());
        assertThat(progressEventCaptor.getAllValues())
                .map(ProvisioningProgressEvent::getEventType)
                .doesNotContain(ProvisioningProgressEvent.EventType.UPDATE);
    }

    @Test
    public void startCountAgainAfterCompleteAndStart() throws Exception {
        // start and complete download phase
        tracker.starting(100);
        adapter.transferSucceeded(new TransferEvent.Builder(session, new TransferResource(null, null, "test.jar", null, null)).build());
        tracker.complete();

        // start another phase
        tracker.starting(2);
        ProvisioningProgressEvent event = getProgressEvent();
        assertEquals(ProvisioningProgressEvent.EventType.STARTING, event.getEventType());
        assertEquals(2, event.getTotal());
        assertEquals(0, event.getCompleted());

        adapter.transferSucceeded(new TransferEvent.Builder(session, new TransferResource(null, null, "test2.jar", null, null)).build());
        event = getProgressEvent();
        assertEquals(ProvisioningProgressEvent.EventType.UPDATE, event.getEventType());
        assertEquals(2, event.getTotal());
        assertEquals(1, event.getCompleted());
        assertEquals("test2.jar", event.getCurrentItem());
    }

    private ProvisioningProgressEvent getProgressEvent() {
        Mockito.verify(console, Mockito.atLeastOnce()).progressUpdate(progressEventCaptor.capture());
        return progressEventCaptor.getValue();
    }

}