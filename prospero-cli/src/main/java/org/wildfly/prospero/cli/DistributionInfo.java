/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.cli;

import java.util.ResourceBundle;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

/**
 * Retrieval of basic distribution information, like the distribution name under which Prospero is distributed to users.
 */
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

}
