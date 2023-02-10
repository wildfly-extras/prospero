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

package org.wildfly.prospero.metadata;

import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.ChannelMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ProsperoMetadataUtils {

    /**
     * Folder containing Prospero metadata inside the provisioned server.
     */
    public static final String METADATA_DIR = ".installation";
    /**
     * Name of the file containing channel manifest generated during provisioning.
     */
    public static final String MANIFEST_FILE_NAME = "manifest.yaml";
    /**
     * Name of the file containing list of channels the server is subscribed to.
     */
    public static final String INSTALLER_CHANNELS_FILE_NAME = "installer-channels.yaml";
    public static final String MAVEN_OPTS_FILE = "maven_opts.json";

    /**
     * Generate installer metadata inside {@code serverDir}. The generated metadata files allow the server to be
     * managed by Prospero.
     *
     * The metadata directory must not contain any metadata files.
     *
     * @param serverDir - base path of the provisioned servers
     * @param channels - list of channels the server should be subscribed to
     * @param manifest - channel manifest containing streams used to provision a server.
     * @throws IOException - if unable to write the metadata files
     * @throws IllegalArgumentException - if any of metadata files are already present.
     */
    public static void generate(Path serverDir, List<Channel> channels, ChannelManifest manifest) throws IOException {
        Objects.requireNonNull(serverDir);
        Objects.requireNonNull(channels);
        Objects.requireNonNull(manifest);

        final Path metadataDir = serverDir.resolve(METADATA_DIR);
        final Path manifestPath = metadataDir.resolve(MANIFEST_FILE_NAME);
        final Path configPath = metadataDir.resolve(INSTALLER_CHANNELS_FILE_NAME);

        if (Files.exists(metadataDir) && !Files.isDirectory(metadataDir)) {
            throw new IllegalArgumentException(String.format("The target path %s is not a directory.", metadataDir));
        }

        if (Files.exists(manifestPath) || Files.exists(configPath)) {
            throw new IllegalArgumentException("Metadata files are already present at " + metadataDir);
        }

        if (!Files.exists(metadataDir)) {
            Files.createDirectory(metadataDir);
        }

        Files.writeString(manifestPath, ChannelManifestMapper.toYaml(manifest), StandardCharsets.UTF_8);
        Files.writeString(configPath, ChannelMapper.toYaml(channels), StandardCharsets.UTF_8);
    }
}
