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

package com.redhat.prospero.cli.actions;

import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.api.SavedState;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.impl.repository.curated.ChannelBuilder;
import com.redhat.prospero.installation.LocalInstallation;
import com.redhat.prospero.maven.MavenUtils;
import com.redhat.prospero.model.XmlException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class InstallationHistory {

    public static void main(String[] args) throws Exception {
        final Path installation = Paths.get(args[1]);
        final String op = args[0];

        final InstallationHistory installationHistory = new InstallationHistory();
        if (op.equals("list")) {
            final List<SavedState> history = installationHistory.getRevisions(installation);

            for (SavedState savedState : history) {
                System.out.println(savedState.shortDescription());
            }
        } else if (op.equals("revert")) {
            final String revision = args[2];
            installationHistory.rollback(installation, new SavedState(revision));
        } else if (op.equals("compare")) {
            final String revision = args[2];
            final List<ArtifactChange> changes = installationHistory.compare(installation, new SavedState(revision));
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

    public List<ArtifactChange> compare(Path installation, SavedState savedState) throws XmlException, ProvisioningException, IOException, MetadataException {
        final LocalInstallation localInstallation = new LocalInstallation(installation);
        return localInstallation.getMetadata().getChangesSince(savedState);
    }

    public List<SavedState> getRevisions(Path installation) throws XmlException, ProvisioningException, IOException, MetadataException {
        final LocalInstallation localInstallation = new LocalInstallation(installation);
        return localInstallation.getMetadata().getRevisions();
    }

    public void rollback(Path installation, SavedState savedState) throws Exception {
        InstallationMetadata metadata = new LocalInstallation(installation).getMetadata();
        metadata = metadata.rollback(savedState);
        final RepositorySystem repositorySystem = MavenUtils.defaultRepositorySystem();
        final DefaultRepositorySystemSession mavenSession = MavenUtils.getDefaultRepositorySystemSession(repositorySystem);
        final Repository repository = new ChannelBuilder(repositorySystem, mavenSession)
                .setChannels(metadata.getChannels())
                .setRestoringManifest(metadata.getManifest())
                .build();
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(repositorySystem, mavenSession, repository);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installation, repoManager);
        provMgr.provision(metadata.getProvisioningConfig());

        // TODO: handle errors - write final state? revert rollback?
    }
}
