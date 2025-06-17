/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero;

/**
 * Utility class to log a version of prospero
 */
public class VersionLogger {

    private static volatile boolean logged = false;
    private static final Object lock = new Object();
    /**
     * Log the version of prospero on startup. This method will only ever create the message once per application.
     */
    public static void logVersionOnStartup() {
        if (!logged) {
            synchronized (lock) {
                if (!logged) {
                    logged = true;
                    try {
                        ProsperoLogger.ROOT_LOGGER.info(
                                "%s version: %s".formatted(DistributionInfo.DIST_NAME, DistributionInfo.getVersion()));
                    } catch (Exception e) {
                        ProsperoLogger.ROOT_LOGGER.warn("Unable to read the prospero version", e);
                    }
                }
            }
        }
    }
}
