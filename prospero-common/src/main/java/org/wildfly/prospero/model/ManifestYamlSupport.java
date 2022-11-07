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

import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

public class ManifestYamlSupport {

    public static ChannelManifest parse(File manifestFile) throws IOException {
        return ChannelManifestMapper.from(manifestFile.toURI().toURL());
    }

    public static void write(ChannelManifest manifest, Path channelFile) throws IOException {
        String yaml = ChannelManifestMapper.toYaml(manifest);
        try (PrintWriter pw = new PrintWriter(new FileWriter(channelFile.toFile()))) {
            pw.println(yaml);
        }
    }
}
