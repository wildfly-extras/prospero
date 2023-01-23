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

package org.wildfly.prospero.galleon;

import org.apache.commons.text.StringSubstitutor;
import org.jboss.logging.Logger;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.exceptions.MetadataException;

import java.net.MalformedURLException;
import java.net.URL;

public class ChannelManifestSubstitutor {

    private static final Logger logger = Logger.getLogger(ChannelManifestSubstitutor.class);

    public static Channel substitute(Channel channel) throws MetadataException {
        ChannelManifestCoordinate channelManifestCoordinate = channel.getManifestCoordinate();
        if (channelManifestCoordinate.getUrl() == null) {
            return channel;
        } else {
            String url = channelManifestCoordinate.getUrl().toString();
            String substitutedFromSystemProperty = StringSubstitutor.replaceSystemProperties(url);
            String substituted = !url.equals(substitutedFromSystemProperty) ? substitutedFromSystemProperty : StringSubstitutor.replace(url, System.getenv());
            if (url.equals(substituted)) {
                return channel;
            } else {
                URL substitutedURL;
                try {
                    substitutedURL = new URL(substituted);
                } catch (MalformedURLException e) {
                    throw Messages.MESSAGES.invalidPropertySubstitutionValue(substituted, url);
                }
                logger.debug("Channel's manifest URL " + url + " is substituted by " + substituted);
                ChannelManifestCoordinate substitutedChannelManifestCoordinate = new ChannelManifestCoordinate(substitutedURL);
                if (channel.getSchemaVersion().isEmpty()) {
                    return new Channel(channel.getName(), channel.getDescription(), channel.getVendor(), channel.getRepositories(), substitutedChannelManifestCoordinate,
                            channel.getBlocklistCoordinate(), channel.getNoStreamStrategy());
                } else {
                    return new Channel(channel.getSchemaVersion(), channel.getName(), channel.getDescription(), channel.getVendor(), channel.getRepositories(), substitutedChannelManifestCoordinate,
                            channel.getBlocklistCoordinate(), channel.getNoStreamStrategy());
                }
            }
        }
    }
}
