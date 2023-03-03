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

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.ProvisioningProgressEvent;

import java.io.File;

import static org.wildfly.prospero.galleon.GalleonEnvironment.TRACK_JB_ARTIFACTS_RESOLVE;

/**
 * Adapter combining Galleon ProgressCallback and Maven TransferListener to track number of already downloaded artifacts.
 *
 * It uses Galleon's TRACK_JB_ARTIFACTS_RESOLVE event to find number of artifacts and Maven's transferSucceeded to update completed
 * count.
 *
 * TODO: the total includes artifacts cached locally - find a way to exclude those or update when they are resolved.
 */
class DownloadsCallbackAdapter extends AbstractTransferListener implements ProgressCallback<MavenArtifact> {

    private final Console console;
    private long totalVolume;
    private long processed;
    private boolean currentPhase = false;

    public DownloadsCallbackAdapter(Console console) {
        this.console = console;
    }

    @Override
    public void starting(ProgressTracker<MavenArtifact> tracker) {
        this.totalVolume = tracker.getTotalVolume();
        this.processed = 0;
        this.currentPhase = true;
        final ProvisioningProgressEvent progress = new ProvisioningProgressEvent(TRACK_JB_ARTIFACTS_RESOLVE, ProvisioningProgressEvent.EventType.STARTING,
                tracker.getProcessedVolume(), tracker.getTotalVolume());
        this.console.progressUpdate(progress);
    }

    @Override
    public void pulse(ProgressTracker<MavenArtifact> progressTracker) {
        // ignore
    }

    @Override
    public void complete(ProgressTracker<MavenArtifact> tracker) {
        final ProvisioningProgressEvent progress = new ProvisioningProgressEvent(TRACK_JB_ARTIFACTS_RESOLVE, ProvisioningProgressEvent.EventType.COMPLETED,
                tracker.getProcessedVolume(), tracker.getTotalVolume());
        this.console.progressUpdate(progress);
        this.totalVolume = 0;
        this.processed = 0;
        this.currentPhase = false;
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        if (!currentPhase) {
            return;
        }

        String item = event.getResource().getResourceName();
        item = item.substring(item.lastIndexOf(File.separator) + 1);
        final ProvisioningProgressEvent progress = new ProvisioningProgressEvent(TRACK_JB_ARTIFACTS_RESOLVE, ProvisioningProgressEvent.EventType.UPDATE,
                ++processed, totalVolume, item, false);
        this.console.progressUpdate(progress);
    }
}
