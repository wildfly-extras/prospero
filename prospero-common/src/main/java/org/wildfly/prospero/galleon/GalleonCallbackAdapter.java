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

import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.ProvisioningProgressEvent;

/**
 * Translates Galleon {@link ProgressCallback} into {@link Console#progressUpdate(ProvisioningProgressEvent)}.
 *
 * New {@code GalleonCallbackAdapter} has to be created for each event type registered for.
 */
public class GalleonCallbackAdapter implements ProgressCallback<Object> {
    private static final int PULSE_INTERVAL = 500;
    private static final int PULSE_PCT = 5;
    private final String id;

    private Console console;

    public GalleonCallbackAdapter(Console console, String id) {
        this.console = console;
        this.id = id;
    }

    @Override
    public long getProgressPulsePct() {
        return PULSE_PCT;
    }

    @Override
    public long getMinPulseIntervalMs() {
        return PULSE_INTERVAL;
    }

    @Override
    public long getMaxPulseIntervalMs() {
        return PULSE_INTERVAL;
    }

    @Override
    public void starting(ProgressTracker tracker) {
        final ProvisioningProgressEvent progress = new ProvisioningProgressEvent(id, ProvisioningProgressEvent.EventType.STARTING, tracker.getProcessedVolume(), tracker.getTotalVolume());
        this.console.progressUpdate(progress);
    }

    @Override
    public void pulse(ProgressTracker tracker) {
        final ProvisioningProgressEvent progress = new ProvisioningProgressEvent(id, ProvisioningProgressEvent.EventType.PULSE, tracker.getProcessedVolume(), tracker.getTotalVolume());
        this.console.progressUpdate(progress);
    }

    @Override
    public void complete(ProgressTracker tracker) {
        final ProvisioningProgressEvent progress = new ProvisioningProgressEvent(id, ProvisioningProgressEvent.EventType.COMPLETED, tracker.getProcessedVolume(), tracker.getTotalVolume());
        this.console.progressUpdate(progress);
    }
}
