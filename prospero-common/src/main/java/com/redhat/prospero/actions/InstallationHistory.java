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

import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.model.ChannelRef;
import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.exceptions.MetadataException;
import com.redhat.prospero.api.SavedState;
import com.redhat.prospero.galleon.GalleonUtils;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.wfchannel.MavenSessionManager;
import com.redhat.prospero.wfchannel.WfChannelMavenResolverFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.redhat.prospero.galleon.GalleonUtils.MAVEN_REPO_LOCAL;

public class InstallationHistory {

    private final Path installation;
    private final Console console;

    public InstallationHistory(Path installation, Console console) {
        this.installation = installation;
        this.console = console;
    }

    public List<ArtifactChange> compare(SavedState savedState) throws MetadataException {
        final InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        return installationMetadata.getChangesSince(savedState);
    }

    public List<SavedState> getRevisions() throws MetadataException {
        final InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        return installationMetadata.getRevisions();
    }

    public void rollback(SavedState savedState, MavenSessionManager mavenSessionManager) throws MetadataException, ProvisioningException {
        InstallationMetadata metadata = new InstallationMetadata(installation);
        metadata = metadata.rollback(savedState);

        final List<Channel> channels = mapToChannels(metadata.getChannels());
        final List<RemoteRepository> repositories = metadata.getRepositories();

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory(mavenSessionManager, repositories);
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(channels, factory, metadata.getChannel());
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installation, repoManager);
        final ProvisioningLayoutFactory layoutFactory = provMgr.getLayoutFactory();

        layoutFactory.setProgressCallback("LAYOUT_BUILD", console.getProgressCallback("LAYOUT_BUILD"));
        layoutFactory.setProgressCallback("PACKAGES", console.getProgressCallback("PACKAGES"));
        layoutFactory.setProgressCallback("CONFIGS", console.getProgressCallback("CONFIGS"));
        layoutFactory.setProgressCallback("JBMODULES", console.getProgressCallback("JBMODULES"));

        try {
            System.setProperty(MAVEN_REPO_LOCAL, mavenSessionManager.getProvisioningRepo().toAbsolutePath().toString());
            provMgr.provision(metadata.getProvisioningConfig());
        } finally {
            System.clearProperty(MAVEN_REPO_LOCAL);
        }

        // TODO: handle errors - write final state? revert rollback?
    }

    private List<Channel> mapToChannels(List<ChannelRef> channelRefs) throws MetadataException {
        final List<Channel> channels = new ArrayList<>();
        for (ChannelRef ref : channelRefs) {
            try {
                channels.add(ChannelMapper.from(new URL(ref.getUrl())));
            } catch (MalformedURLException e) {
                throw new MetadataException("Unable to resolve channel configuration", e);
            }
        } return channels;
    }
}
