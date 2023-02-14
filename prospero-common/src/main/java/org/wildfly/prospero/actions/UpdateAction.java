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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.FileConflict;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.TemporaryRepositoriesHandler;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.updates.UpdateFinder;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

import static org.wildfly.prospero.actions.ApplyCandidateAction.Type.UPDATE;

public class UpdateAction implements AutoCloseable {

    private final InstallationMetadata metadata;
    private final MavenSessionManager mavenSessionManager;
    private final Path installDir;
    private final Console console;
    private final ProsperoConfig prosperoConfig;
    private final MavenOptions mavenOptions;

    public UpdateAction(Path installDir, MavenOptions mavenOptions, Console console, List<Repository> overrideRepositories)
            throws OperationException, ProvisioningException {
        this.metadata = new InstallationMetadata(installDir);
        this.installDir = installDir;
        this.console = console;
        this.prosperoConfig = addTemporaryRepositories(overrideRepositories);
        this.mavenOptions = prosperoConfig.getMavenOptions().merge(mavenOptions);

        this.mavenSessionManager = new MavenSessionManager(this.mavenOptions);
    }

    public List<FileConflict> performUpdate() throws OperationException, ProvisioningException {
        Path targetDir = null;
        try {
            targetDir = Files.createTempDirectory("update-eap");
            if (buildUpdate(targetDir)) {
                ApplyCandidateAction applyCandidateAction = new ApplyCandidateAction(installDir, targetDir);
                return applyCandidateAction.applyUpdate(ApplyCandidateAction.Type.UPDATE);
            } else {
                return Collections.emptyList();
            }
        } catch (IOException e) {
            throw Messages.MESSAGES.unableToCreateTemporaryDirectory(e);
        } finally {
            if (targetDir != null) {
                FileUtils.deleteQuietly(targetDir.toFile());
            }
        }
    }

    public boolean buildUpdate(Path targetDir) throws ProvisioningException, OperationException {
        if (findUpdates().isEmpty()) {
            return false;
        }
        try(final PrepareCandidateAction prepareCandidateAction = new PrepareCandidateAction(installDir, mavenSessionManager, console, prosperoConfig)) {
            return prepareCandidateAction.buildCandidate(targetDir, getGalleonEnv(targetDir), UPDATE);
        }
    }

    private GalleonEnvironment getGalleonEnv(Path target) throws ProvisioningException, OperationException {
        return GalleonEnvironment
                .builder(target, prosperoConfig.getChannels(), mavenSessionManager)
                .setSourceServerPath(this.installDir)
                .setConsole(console)
                .build();
    }

    public UpdateSet findUpdates() throws OperationException, ProvisioningException {
        final GalleonEnvironment galleonEnv = getGalleonEnv(installDir);
        try (final UpdateFinder updateFinder = new UpdateFinder(galleonEnv.getChannelSession(), galleonEnv.getProvisioningManager())) {
            return updateFinder.findUpdates(metadata.getArtifacts());
        }
    }

    @Override
    public void close() {
        metadata.close();
    }

    private ProsperoConfig addTemporaryRepositories(List<Repository> repositories) {
        final ProsperoConfig prosperoConfig = metadata.getProsperoConfig();

        final List<Channel> channels = TemporaryRepositoriesHandler.overrideRepositories(prosperoConfig.getChannels(), repositories);

        return new ProsperoConfig(channels, prosperoConfig.getMavenOptions());
    }
}
