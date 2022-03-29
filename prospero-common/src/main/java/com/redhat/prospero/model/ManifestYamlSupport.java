/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.model;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.api.Manifest;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.Stream;
import org.wildfly.channel.Vendor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ManifestYamlSupport {

    public static Manifest parse(File manifestFile) throws IOException {
        final Channel channel = ChannelMapper.from(manifestFile.toURI().toURL());
        List<Artifact> artifacts = new ArrayList<>();
        for (Stream stream : channel.getStreams()) {
            Artifact a = new DefaultArtifact(stream.getGroupId(), stream.getArtifactId(), null, stream.getVersion());
            artifacts.add(a);
        }
        return new Manifest(artifacts, manifestFile.toPath());
    }

    public static void write(Manifest manifest) throws IOException {
        write(manifest, Collections.emptyList());
    }

    public static void write(Manifest manifest, List<ChannelRef> channelRefs) throws IOException {
        List<Stream> streams = new ArrayList<>();
        for (Artifact resolvedArtifact : manifest.getArtifacts()) {
            streams.add(new Stream(resolvedArtifact.getGroupId(), resolvedArtifact.getArtifactId(), resolvedArtifact.getVersion(), null));
        }
        
        Set<MavenRepository> repositories = new HashSet<>();
        for (ChannelRef channelRef : channelRefs) {
            final Channel channel = ChannelMapper.from(new URL(channelRef.getUrl()));
            repositories.addAll(channel.getRepositories());
        }

        final Channel channel = new Channel("provisioned", "provisioned", "",
                new Vendor("Custom", Vendor.Support.COMMUNITY),
                true, null, new ArrayList<>(repositories), streams);
        String yaml = ChannelMapper.toYaml(channel);
        try (PrintWriter pw = new PrintWriter(new FileWriter(manifest.getManifestFile().toFile()))) {
            pw.println(yaml);
        }
    }
}
