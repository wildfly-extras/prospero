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

package org.wildfly.prospero.api;

import org.apache.commons.lang3.StringUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.channel.Repository;

import java.util.ArrayList;
import java.util.List;

public class ChannelChange extends Diff {

    private final Channel old;
    private final Channel current;

    public static ChannelChange added(Channel current) {
        return new ChannelChange(null, current);
    }

    public static ChannelChange modified(Channel old, Channel current) {
        return new ChannelChange(old, current);
    }

    public static ChannelChange removed(Channel old) {
        return new ChannelChange(old, null);
    }

    public Channel getOldChannel() {
        return old;
    }

    public Channel getNewChannel() {
        return current;
    }

    private ChannelChange(Channel old, Channel current) {
        super(getName(old, current), getStatus(old, current), diffChildren(old, current));
        this.old = old;
        this.current = current;
    }

    private static List<Diff> diffChildren(Channel old, Channel current) {
        List<Diff> children = new ArrayList<>();
        final String oldManifest = getManifest(old);
        final String currentManifest = getManifest(current);
        if (!StringUtils.equals(oldManifest, currentManifest)) {
            children.add(new Diff("manifest", oldManifest, currentManifest));
        }
        final List<Diff> repositoriesDiffs = repositoriesDiffs(old, current);
        if (!repositoriesDiffs.isEmpty()) {
            children.add(new Diff("repositories", Diff.Status.MODIFIED, repositoriesDiffs));
        }

        return children;
    }

    private static List<Diff> repositoriesDiffs(Channel channel1, Channel channel2) {
        List<Diff> repoDiffs = new ArrayList<>();
        if (channel1 != null && channel2 != null) {
            // as order of repositories matter we compare them by position rather then name
            for (int i = 0; i < channel1.getRepositories().size(); i++) {
                final Repository r1 = channel1.getRepositories().get(i);
                final Repository r2 = channel2.getRepositories().size() > i ? channel2.getRepositories().get(i) : null;
                final String r1Name = r1.getId() + "::" + r1.getUrl();
                final String r2Name = r2 == null ? null : r2.getId() + "::" + r2.getUrl();
                // only add if they're changed
                if (!r1Name.equals(r2Name)) {
                    repoDiffs.add(new Diff(null, r1Name, r2Name));
                }
            }
            // add repositories not present in channel1
            for (int i = channel1.getRepositories().size(); i < channel2.getRepositories().size(); i++) {
                Repository r = channel2.getRepositories().get(i);
                repoDiffs.add(new Diff(null, null, r.getId() + "::" + r.getUrl()));
            }
        } else if (channel1 == null) {
            // add all repositories from added channel
            for (Repository r : channel2.getRepositories()) {
                repoDiffs.add(new Diff(null, null, r.getId() + "::" + r.getUrl()));
            }
        } else {
            // add all repositories from removed channel
            for (Repository r : channel1.getRepositories()) {
                repoDiffs.add(new Diff(null, r.getId() + "::" + r.getUrl(), null));
            }
        }
        return repoDiffs;
    }

    private static String getName(Channel channel1, Channel channel2) {
        return channel1 != null?channel1.getName():channel2.getName();
    }

    private static Status getStatus(Channel old, Channel current) {
        return old == null ? Status.ADDED : current == null ? Status.REMOVED : Status.MODIFIED;
    }

    private static String getManifest(Channel channel) {
        if (channel == null) {
            return null;
        }
        if (channel.getManifestCoordinate() == null) {
            return null;
        }
        return channel.getManifestCoordinate().getMaven() == null
                ? channel.getManifestCoordinate().getUrl().toExternalForm() : toGav(channel.getManifestCoordinate().getMaven());
    }

    private static String toGav(MavenCoordinate coord) {
        final String ga = coord.getGroupId() + ":" + coord.getArtifactId();
        if (coord.getVersion() != null && !coord.getVersion().isEmpty()) {
            return ga + ":" + coord.getVersion();
        }
        return ga;
    }
}
