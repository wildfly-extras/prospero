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

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.jar.Manifest;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

public class DistributionInfo {

    private static final String PROSPERO_DIST_NAME_KEY = "prospero.dist.name";
    private static final String DEFAULT_DIST_NAME = "prospero";
    private static final Logger LOG = Logger.getLogger(DistributionInfo.class);

    public static final String DIST_NAME;

    static {
        ResourceBundle usageMessages = ResourceBundle.getBundle("UsageMessages");
        if (usageMessages != null) {
            String distName = usageMessages.getString(PROSPERO_DIST_NAME_KEY);
            if (StringUtils.isNotBlank(distName)) {
                DIST_NAME = distName;
            } else {
                LOG.warnf("Distribution name was not defined.");
                DIST_NAME = DEFAULT_DIST_NAME;
            }
        } else {
            LOG.warnf("UsageMessages bundle couldn't be located, unable to retrieve distribution name.");
            DIST_NAME = DEFAULT_DIST_NAME;
        }
    }

    public static String getVersion() throws Exception {
        final Enumeration<URL> resources = DistributionInfo.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
        while (resources.hasMoreElements()) {
            final URL url = resources.nextElement();
            final Manifest manifest = new Manifest(url.openStream());
            final String specTitle = manifest.getMainAttributes().getValue("Specification-Title");
            if ("prospero-common".equals(specTitle) || "prospero-cli".equals(specTitle)) {
                return StringUtils.join(manifest.getMainAttributes().getValue("Implementation-Version"));
            }
        }

        return "unknown";
    }

    public static Stability getStability() {
        if (stability == null) {
            synchronized (KEY) {
                if (stability == null) {
                    stability = loadStability();
                }
            }
        }
        return stability;
    }

    public static Stability getMinStability() {
        return MinStabilityHolder.stability;
    }

    private static volatile Stability stability;
    private static final Object KEY = new Object();

    public static void setStability(Stability overrideStability) {
        if (!getMinStability().permits(overrideStability)) {
            throw new IllegalStateException("The requested stability level %s is not allowed by this distribution. The minimum supported stability level is %s"
                    .formatted(overrideStability, getMinStability()));
        }

        if (stability == null) {
            synchronized (KEY) {
                if (stability == null) {
                    final Stability defaultLevel = loadStability();
                    if (!defaultLevel.permits(Stability.Community)) {
                        throw new IllegalStateException("Changing stability levels is not allowed at %s stability level.".formatted(defaultLevel));
                    }
                    stability = overrideStability;
                } else if (stability != overrideStability) {
                    throw new IllegalStateException("Attempting to set the stability level after it was already set.");
                }
            }
        } else if (stability != overrideStability) {
            throw new IllegalStateException("Attempting to set the stability level after it was already set.");
        }
    }

    private static final class MinStabilityHolder {
        private static final Stability stability = loadMinStability();

    }

    private static Stability loadStability() {
        try {
            final Enumeration<URL> resources = DistributionInfo.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                final URL url = resources.nextElement();
                final Manifest manifest = new Manifest(url.openStream());
                final String stability = manifest.getMainAttributes().getValue("JBoss-Product-Stability");
                if (stability != null) {
                    return Stability.from(stability);
                }
            }

            return Stability.Default;
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the distribution info.", e);
        }
    }

    private static Stability loadMinStability() {
        try {
            final Enumeration<URL> resources = DistributionInfo.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                final URL url = resources.nextElement();
                final Manifest manifest = new Manifest(url.openStream());
                final String stability = manifest.getMainAttributes().getValue("JBoss-Product-Minimal-Stability");
                if (stability != null) {
                    return Stability.from(stability);
                }
            }

            return Stability.Default;
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the distribution info.", e);
        }
    }

}
