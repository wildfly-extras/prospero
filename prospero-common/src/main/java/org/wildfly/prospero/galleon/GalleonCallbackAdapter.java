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
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.ProvisioningProgressEvent;

import java.nio.file.Path;
import java.util.List;

import static org.jboss.galleon.Constants.TRACK_CONFIGS;
import static org.jboss.galleon.Constants.TRACK_LAYOUT_BUILD;
import static org.jboss.galleon.Constants.TRACK_PACKAGES;
import org.jboss.galleon.api.config.GalleonProvisionedConfig;
import static org.wildfly.prospero.galleon.GalleonEnvironment.TRACK_JBEXAMPLES;
import static org.wildfly.prospero.galleon.GalleonEnvironment.TRACK_JBMODULES;

/**
 * Translates Galleon {@link ProgressCallback} into {@link Console#progressUpdate(ProvisioningProgressEvent)}.
 *
 * New {@code GalleonCallbackAdapter} has to be created for each event type registered for.
 */
@SuppressWarnings("PMD.TooManyStaticImports")
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
        final ProvisioningProgressEvent progress = new ProvisioningProgressEvent(id, ProvisioningProgressEvent.EventType.STARTING,
                tracker.getProcessedVolume(), tracker.getTotalVolume());
        final String total =  tracker.getTotalVolume()>0 ? ""+tracker.getTotalVolume() : "";
        ProsperoLogger.ROOT_LOGGER.startedPhase(name(id), total);

        if (console != null) {
            this.console.progressUpdate(progress);
        }
    }

    @Override
    public void pulse(ProgressTracker tracker) {
    }

    @Override
    public void complete(ProgressTracker tracker) {
        final ProvisioningProgressEvent progress = new ProvisioningProgressEvent(id, ProvisioningProgressEvent.EventType.COMPLETED,
                tracker.getProcessedVolume(), tracker.getTotalVolume());
        final String processed = tracker.getProcessedVolume()>0 ? ""+tracker.getProcessedVolume() : "";
        ProsperoLogger.ROOT_LOGGER.completedPhase(name(id), processed);

        if (console != null) {
            this.console.progressUpdate(progress);
        }
    }

    @Override
    public void processing(ProgressTracker<Object> tracker) {
        if (console == null) {
            return;
        }

        String item = null;
        boolean slowPhase = false;

        switch (id) {
            case TRACK_LAYOUT_BUILD:
                item = ((FeaturePackLocation.FPID)tracker.getItem()).getLocation().toString();
                break;
            case TRACK_CONFIGS:
                if (tracker.getItem() != null) {
                    item = ((GalleonProvisionedConfig) tracker.getItem()).getModel() + "/" + ((GalleonProvisionedConfig) tracker.getItem()).getName();
                } else {
                    slowPhase = true;
                }
                break;
            case TRACK_JBEXAMPLES:
                List<Object> items = (List<Object>) tracker.getItem();
                if(items.get(1) instanceof GalleonProvisionedConfig) {
                    item = "Generating " + ((GalleonProvisionedConfig) items.get(1)).getName();
                } else if (items.get(1) instanceof Path) {
                    item = "Installing config " + ((Path) items.get(1)).getFileName();
                }
                if (items.get(0).equals(TRACK_CONFIGS)) {
                    slowPhase = true;
                }
                break;
            case GalleonEnvironment.TRACK_JB_ARTIFACTS_RESOLVE:
                item = tracker.getItem() != null?tracker.getItem().toString():"";
                break;
        }
        final ProvisioningProgressEvent progress = new ProvisioningProgressEvent(id, ProvisioningProgressEvent.EventType.UPDATE,
                tracker.getProcessedVolume(), tracker.getTotalVolume(), item, slowPhase);
        this.console.progressUpdate(progress);
    }

    private String name(String id) {
        switch (id) {
            case TRACK_LAYOUT_BUILD:
                return "build layout";
            case TRACK_PACKAGES:
                return "install packages";
            case TRACK_JBMODULES:
                return "install modules";
            case TRACK_CONFIGS:
                return "generate configuration";
            case TRACK_JBEXAMPLES:
                return "generate examples";
            default:
                return id;
        }
    }
}
