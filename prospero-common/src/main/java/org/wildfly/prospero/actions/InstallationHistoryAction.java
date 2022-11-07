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

package org.wildfly.prospero.actions;

import org.jboss.galleon.ProvisioningManager;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

import java.nio.file.Path;
import java.util.List;

import static org.wildfly.prospero.galleon.GalleonUtils.MAVEN_REPO_LOCAL;

public class InstallationHistoryAction {

    private final Path installation;
    private final Console console;

    public InstallationHistoryAction(Path installation, Console console) {
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

    public void rollback(SavedState savedState, MavenSessionManager mavenSessionManager) throws OperationException, ProvisioningException {
        InstallationMetadata metadata = new InstallationMetadata(installation);
        try {
            metadata = metadata.rollback(savedState);
            final ProsperoConfig prosperoConfig = metadata.getProsperoConfig();
            final GalleonEnvironment galleonEnv = GalleonEnvironment
                    .builder(installation, prosperoConfig, mavenSessionManager)
                    .setConsole(console)
                    .setRestoreManifest(metadata.getManifest())
                    .build();

            System.setProperty(MAVEN_REPO_LOCAL, mavenSessionManager.getProvisioningRepo().toAbsolutePath().toString());
            final ProvisioningManager provMgr = galleonEnv.getProvisioningManager();
            try {
                GalleonUtils.executeGalleon(options -> provMgr.provision(provMgr.getProvisioningConfig(), options),
                        mavenSessionManager.getProvisioningRepo().toAbsolutePath());
            } catch (UnresolvedMavenArtifactException e) {
                throw new ArtifactResolutionException(e, prosperoConfig.listAllRepositories(), mavenSessionManager.isOffline());
            }
        } finally {
            System.clearProperty(MAVEN_REPO_LOCAL);
            metadata.close();
        }

        // TODO: handle errors - write final state? revert rollback?
    }
}
