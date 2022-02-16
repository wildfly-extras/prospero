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

package com.redhat.prospero.actions;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.galleon.GalleonUtils;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.wfchannel.WfChannelMavenResolverFactory;
import org.eclipse.aether.artifact.Artifact;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.redhat.prospero.api.ArtifactUtils.from;
import static com.redhat.prospero.galleon.GalleonUtils.MAVEN_REPO_LOCAL;

public class InstallationRestore {

    private final Path installDir;

    public InstallationRestore(Path installDir) {
        this.installDir = installDir;
    }

    public static void main(String[] args) throws Exception {

        String targetDir = args[0];
        String metadataBundle = args[1];

        new InstallationRestore(Paths.get(targetDir)).restore(Paths.get(metadataBundle));
    }

    public void restore(Path metadataBundleZip)
            throws ProvisioningException, IOException, MetadataException {
        if (installDir.toFile().exists()) {
            throw new ProvisioningException("Installation dir " + installDir + " already exists");
        }

        final InstallationMetadata metadataBundle = InstallationMetadata.importMetadata(metadataBundleZip);

        final List<ChannelRef> channelRefs = metadataBundle.getChannels();
        final List<Channel> channels = channelRefs.stream().map(ref-> {
            try {
                return ChannelMapper.from(new URL(ref.getUrl()));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory();
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(channels, factory, metadataBundle.getManifest());

        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, repoManager);

        try {
            System.setProperty(MAVEN_REPO_LOCAL, factory.getProvisioningRepo().toAbsolutePath().toString());
            provMgr.provision(metadataBundle.getProvisioningConfig());
        } finally {
            System.clearProperty(MAVEN_REPO_LOCAL);
        }
        writeProsperoMetadata(repoManager, metadataBundle.getChannels());
    }

    private void writeProsperoMetadata(ChannelMavenArtifactRepositoryManager maven, List<ChannelRef> channelRefs)
            throws MetadataException {
        List<Artifact> artifacts = new ArrayList<>();
        for (MavenArtifact resolvedArtifact : maven.resolvedArtfacts()) {
            artifacts.add(from(resolvedArtifact));
        }

        new InstallationMetadata(installDir, artifacts, channelRefs).writeFiles();
    }
}
