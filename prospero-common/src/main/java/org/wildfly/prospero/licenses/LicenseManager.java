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

package org.wildfly.prospero.licenses;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class LicenseManager {

    private static final Logger logger = Logger.getLogger(LicenseManager.class);
    private final HashMap<String, List<License>> nameMap = new HashMap();

    public LicenseManager() {
        this(getLicensesFile());
    }

    LicenseManager(URL licensesUrl) {
        if (licensesUrl == null) {
            logger.debug("No known repositories found");
        } else {
            logger.debug("Loading known provisioning configurations from: " + licensesUrl);
            final List<License> knownFeaturePacks;
            try {
                knownFeaturePacks = License.readLicenses(licensesUrl);
                for (License fp : knownFeaturePacks) {
                    if (!nameMap.containsKey(fp.getFpGav())) {
                        nameMap.put(fp.getFpGav(), new ArrayList<>());
                    }
                    nameMap.get(fp.getFpGav()).add(fp);
                }
            } catch (IOException e) {
                logger.warn("Failed to load licenses configurations from: " + licensesUrl);
                logger.debug("Error parsing provisioning configurations:", e);
            }
        }
    }

    private static URL getLicensesFile() {
        final Locale locale = Locale.getDefault();
        URL licensesUrl = LicenseManager.class.getClassLoader().getResource("licenses_" + locale.getLanguage() + ".yaml");
        if (licensesUrl == null) {
            return LicenseManager.class.getClassLoader().getResource("licenses.yaml");
        } else {
            return licensesUrl;
        }
    }

    public List<License> getLicenses(List<String> fpls) {
        return fpls.stream()
                .filter(nameMap::containsKey)
                .map(nameMap::get)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
