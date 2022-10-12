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

import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.updates.UpdateFinder;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;

public class UpdateAction implements AutoCloseable {

    private final InstallationMetadata metadata;

    private final Console console;
    private final MavenSessionManager mavenSessionManager;
    private final GalleonEnvironment galleonEnv;
    private final ProsperoConfig prosperoConfig;

    public UpdateAction(Path installDir, MavenSessionManager mavenSessionManager, Console console) throws ProvisioningException, OperationException {
        this(installDir, mavenSessionManager, console, Collections.emptyList());
    }

    // Option for BETA update support
    // TODO: evaluate in GA - replace by repository:add / custom channels?
    public UpdateAction(Path installDir, MavenSessionManager mavenSessionManager, Console console, List<URL> additionalRepositories)
            throws ProvisioningException, OperationException {
        this.metadata = new InstallationMetadata(installDir);

        this.prosperoConfig = addTemporaryRepositories(additionalRepositories);
        galleonEnv = GalleonEnvironment
                .builder(installDir, prosperoConfig, mavenSessionManager)
                .setConsole(console)
                .build();
        this.mavenSessionManager = mavenSessionManager;
        this.console = console;
    }

    private ProsperoConfig addTemporaryRepositories(List<URL> additionalRepositories) {
        final ProsperoConfig prosperoConfig = metadata.getProsperoConfig();
        for (Channel channel : prosperoConfig.getWfChannels()) {
            int i = 0;
            final Set<String> existingRepos = channel.getRepositories().stream().map(Repository::getUrl).collect(Collectors.toSet());
            for (URL additionalRepository : additionalRepositories) {
                if (!existingRepos.contains(additionalRepository.toString())) {
                    channel.getRepositories().add(new Repository("temp-repo-"+i++, additionalRepository.toString()));
                }
            }
        }
        return prosperoConfig;
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

        metadata.recordProvision(false);

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

    protected void applyUpdates() throws ProvisioningException, ArtifactResolutionException {
        final ProvisioningManager provMgr = galleonEnv.getProvisioningManager();
        try {
            GalleonUtils.executeGalleon(options -> provMgr.provision(provMgr.getProvisioningConfig(), options),
                    mavenSessionManager.getProvisioningRepo().toAbsolutePath());
        } catch (UnresolvedMavenArtifactException e) {
            throw new ArtifactResolutionException(e, prosperoConfig.getRemoteRepositories(), mavenSessionManager.isOffline());
        }

        metadata.setManifest(galleonEnv.getRepositoryManager().resolvedChannel());
    }

    @Override
    public void close() throws Exception {
        metadata.close();
    }
}
