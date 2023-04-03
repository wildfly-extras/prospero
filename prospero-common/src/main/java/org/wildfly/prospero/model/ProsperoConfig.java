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

package org.wildfly.prospero.model;

import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.InvalidChannelMetadataException;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProsperoConfig {

    private final List<Channel> channels;
    private final MavenOptions mavenOptions;

    public ProsperoConfig(List<Channel> channels) {
        this.channels = channels;
        this.mavenOptions = null;
    }

    public ProsperoConfig(List<Channel> channels, MavenOptions mavenOptions) {
        this.channels = channels;
        this.mavenOptions = mavenOptions;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    public static ProsperoConfig readConfig(Path path) throws MetadataException {
        final String yamlContent;
        MavenOptions opts = null;
        try {
            yamlContent = Files.readString(path.resolve(ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME)).trim();
        } catch (IOException e) {
            throw ProsperoLogger.ROOT_LOGGER.unableToReadFile(path, e);
        }

        if (Files.exists(path.resolve(ProsperoMetadataUtils.MAVEN_OPTS_FILE))) {
            try {
                opts = MavenOptions.read(path.resolve(ProsperoMetadataUtils.MAVEN_OPTS_FILE));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (yamlContent.isEmpty()) {
            return new ProsperoConfig(Collections.emptyList(), opts);
        } else {
            try {
                return new ProsperoConfig(ChannelMapper.fromString(yamlContent), opts);
            } catch (InvalidChannelMetadataException e) {
                throw ProsperoLogger.ROOT_LOGGER.unableToParseConfiguration(path.resolve(ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME), e.getCause());
            }
        }
    }

    public Collection<RemoteRepository> listAllRepositories() {
        return channels.stream()
                .flatMap(c->c.getRepositories().stream())
                .map(r->new RemoteRepository.Builder(r.getId(), "default", r.getUrl()).build())
                .collect(Collectors.toSet());
    }

    public MavenOptions getMavenOptions() {
        return mavenOptions==null?MavenOptions.DEFAULT_OPTIONS:mavenOptions;
    }
}
