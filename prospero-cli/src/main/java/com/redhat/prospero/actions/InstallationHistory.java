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
import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.api.SavedState;
import com.redhat.prospero.galleon.GalleonUtils;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.wfchannel.WfChannelMavenResolverFactory;
import org.jboss.galleon.ProvisioningManager;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.redhat.prospero.galleon.GalleonUtils.MAVEN_REPO_LOCAL;

public class InstallationHistory {

    private final Path installation;

    public InstallationHistory(Path installation) {
        this.installation = installation;
    }

    public static void main(String[] args) throws Exception {
        final Path installation = Paths.get(args[1]);
        final String op = args[0];

        final InstallationHistory installationHistory = new InstallationHistory(installation);
        if (op.equals("list")) {
            final List<SavedState> history = installationHistory.getRevisions();

            for (SavedState savedState : history) {
                System.out.println(savedState.shortDescription());
            }
        } else if (op.equals("revert")) {
            final String revision = args[2];
            installationHistory.rollback(new SavedState(revision));
        } else if (op.equals("compare")) {
            final String revision = args[2];
            final List<ArtifactChange> changes = installationHistory.compare(new SavedState(revision));
            if (changes.isEmpty()) {
                System.out.println("No changes found");
            } else {
                changes.forEach((c-> System.out.println(c)));
            }
        } else {
            System.out.println("Unknown operation " + op);
            System.exit(-1);
        }


    }

    public List<ArtifactChange> compare(SavedState savedState) throws MetadataException {
        final InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        return installationMetadata.getChangesSince(savedState);
    }

    public List<SavedState> getRevisions() throws MetadataException {
        final InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        return installationMetadata.getRevisions();
    }

    public void rollback(SavedState savedState) throws Exception {
        InstallationMetadata metadata = new InstallationMetadata(installation);
        metadata = metadata.rollback(savedState);

        final List<ChannelRef> channelRefs = metadata.getChannels();
        final List<Channel> channels = channelRefs.stream().map(ref-> {
            try {
                return ChannelMapper.from(new URL(ref.getUrl()));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory();
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(channels, factory);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installation, repoManager);

        try {
            System.setProperty(MAVEN_REPO_LOCAL, factory.getProvisioningRepo().toAbsolutePath().toString());
            provMgr.provision(metadata.getProvisioningConfig());
        } finally {
            System.clearProperty(MAVEN_REPO_LOCAL);
        }

        // TODO: handle errors - write final state? revert rollback?
    }

}
