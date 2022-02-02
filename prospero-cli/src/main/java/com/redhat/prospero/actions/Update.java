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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.redhat.prospero.api.ArtifactUtils;
import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.cli.CliConsole;
import com.redhat.prospero.cli.Console;
import com.redhat.prospero.galleon.GalleonUtils;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.model.XmlException;
import com.redhat.prospero.wfchannel.WfChannelMavenResolverFactory;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.ProvisioningPlan;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.UnresolvedMavenArtifactException;

public class Update {

    private final InstallationMetadata metadata;
    private final ChannelMavenArtifactRepositoryManager maven;
    private final ProvisioningManager provMgr;
    private final ChannelSession channelSession;

    private final Console console;

    public Update(Path installDir, Console console) throws ProvisioningException, MetadataException {
        this.metadata = new InstallationMetadata(installDir);
        final List<ChannelRef> channelRefs = metadata.getChannels();
        final List<Channel> channels = channelRefs.stream().map(ref-> {
            try {
                return ChannelMapper.from(new URL(ref.getUrl()));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory();
        this.channelSession = new ChannelSession(channels, factory);
        this.maven = new ChannelMavenArtifactRepositoryManager(channelSession);
        this.provMgr = GalleonUtils.getProvisioningManager(installDir, maven);
        this.console = console;
    }

    public void doUpdateAll() throws ArtifactNotFoundException, XmlException, ProvisioningException, IOException, MetadataException, UnresolvedMavenArtifactException {
        final List<ArtifactChange> updates = new ArrayList<>();
        final Manifest manifest = metadata.getManifest();
        for (Artifact artifact : manifest.getArtifacts()) {
            updates.addAll(findUpdates(artifact));
        }
        final ProvisioningPlan fpUpdates = findFPUpdates();

        console.updatesFound(fpUpdates.getUpdates(), updates);
        if (updates.isEmpty() && fpUpdates.isEmpty()) {
            return;
        }

        if (!console.confirmUpdates()) {
            return;
        }

        applyFpUpdates(fpUpdates);

        metadata.writeFiles();

        console.updatesComplete();
    }

    public List<ArtifactChange> findUpdates(Artifact artifact) throws ArtifactNotFoundException, UnresolvedMavenArtifactException {
        List<ArtifactChange> updates = new ArrayList<>();

        if (artifact == null) {
            throw new ArtifactNotFoundException(String.format("Artifact [%s:%s] not found", artifact.getGroupId(), artifact.getArtifactId()));
        }

        final org.wildfly.channel.MavenArtifact latestVersion = channelSession.resolveLatestMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion());
        final Artifact latest = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), latestVersion.getExtension(), latestVersion.getVersion());


        if (latestVersion == null || ArtifactUtils.compareVersion(latest, artifact) <= 0) {
            return updates;
        }

        updates.add(new ArtifactChange(artifact, latest));

        return updates;
    }

    private ProvisioningPlan findFPUpdates() throws ProvisioningException, IOException {
        return provMgr.getUpdates(true);
    }

    private Set<Artifact> applyFpUpdates(ProvisioningPlan updates) throws ProvisioningException, IOException {
        provMgr.apply(updates);

        final Set<MavenArtifact> resolvedArtfacts = maven.resolvedArtfacts();

        // filter out non-installed artefacts
        final Set<Artifact> collected = resolvedArtfacts.stream()
                .map(a->new DefaultArtifact(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion()))
                .filter(a->(!a.getArtifactId().equals("wildfly-producers") && !a.getArtifactId().equals("community-universe")))
                .collect(Collectors.toSet());
        metadata.registerUpdates(collected);
        return collected;
    }

}
