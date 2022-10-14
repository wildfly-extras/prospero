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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProsperoConfig {
    private List<Channel> channels;

    public ProsperoConfig(List<Channel> channels) {
        this.channels = channels;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    public void writeConfig(Path configFile) throws IOException {
        Files.writeString(configFile, ChannelMapper.toYaml(channels));
    }

    public static ProsperoConfig readConfig(Path path) throws IOException {
        final String yamlContent = Files.readString(path);
        if (yamlContent.isEmpty()) {
            return new ProsperoConfig(Collections.emptyList());
        } else {
            return new ProsperoConfig(ChannelMapper.fromString(yamlContent));
        }
    }

    public Collection<RemoteRepository> listAllRepositories() {
        return channels.stream()
                .flatMap(c->c.getRepositories().stream())
                .map(r->new RemoteRepository.Builder(r.getId(), "default", r.getUrl()).build())
                .collect(Collectors.toSet());
    }
}
