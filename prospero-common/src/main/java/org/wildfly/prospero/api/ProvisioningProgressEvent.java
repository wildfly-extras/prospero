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

package org.wildfly.prospero.api;

/**
 * Represents provisioning progress. Emitted periodically during provisioning of servers.
 */
public class ProvisioningProgressEvent {

    public enum EventType {
        /**
         * new stage has been started
         */
        STARTING,
        /**
         * progress update on current stage
         */
        UPDATE,
        /**
         * current stage has been finished
         */
        COMPLETED;

    }

    private final long completed;
    private final long total;
    private final String stage;
    private final EventType eventType;
    private String item = null;
    private boolean slowPhase = false;

    public ProvisioningProgressEvent(String stage, EventType eventType, long completed, long total) {
        this.completed = completed;
        this.total = total;
        this.stage = stage;
        this.eventType = eventType;
    }

    public ProvisioningProgressEvent(String stage, EventType eventType, long completed, long total, String item, boolean slowPhase) {
        this(stage, eventType, completed, total);
        this.item = item;
        this.slowPhase = slowPhase;
    }

    /**
     * details of currently processed item.
     *
     * @return description of item if available, null otherwise
     */
    public String getCurrentItem() {
        return item;
    }

    /**
     * if set, the current phase doesn't receive any updates and can be a relatively slow process
     *
     * @return
     */
    public boolean isSlowPhase() {
        return slowPhase;
    }

    /**
     * percentage of completion at the time of emitting the event
     * @return
     */
    public double getProgress() {
        if (total < 0) {
            return 0;
        }

        return (double)completed / (double)total * 100;
    }

    /**
     * checks whether the stage provides progress updates
     *
     * @return true if the count of events will be updated in PULSE events
     */
    boolean isMeasurable() {
        return total >0;
    }

    /**
     * type of stage event - see {@link EventType}
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * count of completed items when the even is emitted
     */
    public long getCompleted() {
        return completed;
    }

    /**
     * total count of items to complete in current stage
     */
    public long getTotal() {
        return total;
    }

    /**
     * name of the current stage
     */
    public String getStage() {
        return stage;
    }

    @Override
    public String toString() {
        return "ProvisioningProgress{" +
                "completed=" + completed +
                ", total=" + total +
                ", stage='" + stage + '\'' +
                ", event=" + eventType +
                '}';
    }
}
