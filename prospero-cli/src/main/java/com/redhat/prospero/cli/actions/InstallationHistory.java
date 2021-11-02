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

import com.redhat.prospero.api.InstallationMetadata;
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
        final List<SavedState> history = new InstallationHistory().getRevisions(Paths.get(args[0]));

        for (SavedState savedState : history) {
            System.out.println(savedState.shortDescription());
        }
    }

    public List<SavedState> getRevisions(Path installation) throws XmlException, ProvisioningException, IOException {
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
                .setRestoringManifest(savedState.getMetadata())
                .build();
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(repositorySystem, mavenSession, repository);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installation, repoManager);
        provMgr.provision(metadata.getProvisioningConfig());

        // TODO: handle errors - write final state? revert rollback?
    }
}