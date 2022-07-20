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

package org.wildfly.prospero.actions;

import java.nio.file.Path;

import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.updates.UpdateFinder;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;

public class UpdateAction {

    private final InstallationMetadata metadata;

    private final Console console;
    private final MavenSessionManager mavenSessionManager;
    private final GalleonEnvironment galleonEnv;

    public UpdateAction(Path installDir, MavenSessionManager mavenSessionManager, Console console) throws ProvisioningException, OperationException {
        this.metadata = new InstallationMetadata(installDir);
        galleonEnv = GalleonEnvironment
                .builder(installDir, metadata.getProsperoConfig(), mavenSessionManager)
                .setConsole(console)
                .build();
        this.mavenSessionManager = mavenSessionManager;
        this.console = console;
    }

    public void doUpdateAll(boolean confirmed) throws ProvisioningException, MetadataException, ArtifactResolutionException {
        final UpdateSet updateSet = findUpdates();

        console.updatesFound(updateSet.getFpUpdates().getUpdates(), updateSet.getArtifactUpdates());
        if (updateSet.isEmpty()) {
            return;
        }

        if (!confirmed && !console.confirmUpdates()) {
            return;
        }

        applyUpdates();

        metadata.recordProvision();

        console.updatesComplete();
    }

    public void listUpdates() throws ArtifactResolutionException, ProvisioningException {
        final UpdateSet updateSet = findUpdates();

        console.updatesFound(updateSet.getFpUpdates().getUpdates(), updateSet.getArtifactUpdates());
    }

    protected UpdateSet findUpdates() throws ArtifactResolutionException, ProvisioningException {
        try (final UpdateFinder updateFinder = new UpdateFinder(galleonEnv.getChannelSession(), galleonEnv.getProvisioningManager())) {
            return updateFinder.findUpdates(metadata.getArtifacts());
        }
    }

    protected void applyUpdates() throws ProvisioningException {
        final ProvisioningManager provMgr = galleonEnv.getProvisioningManager();
        GalleonUtils.executeGalleon(options -> provMgr.provision(provMgr.getProvisioningConfig(), options),
                mavenSessionManager.getProvisioningRepo().toAbsolutePath());

        metadata.setChannel(galleonEnv.getRepositoryManager().resolvedChannel());
    }

}
