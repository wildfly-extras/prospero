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
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.TemporaryRepositoriesHandler;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

public class UpdateAction implements AutoCloseable {

    private final InstallationMetadata metadata;
    private final MavenSessionManager mavenSessionManager;
    private final Path installDir;
    private final Console console;
    private final List<Repository> overrideRepositories;

    public UpdateAction(Path installDir, MavenSessionManager mavenSessionManager, Console console, List<Repository> overrideRepositories)
            throws OperationException {
        this.metadata = new InstallationMetadata(installDir);
        this.installDir = installDir;
        this.console = console;
        this.overrideRepositories = overrideRepositories;
        this.mavenSessionManager = mavenSessionManager;
    }

    public void performUpdate() throws OperationException, ProvisioningException, MetadataException, ArtifactResolutionException {
        Path targetDir = null;
        try {
            targetDir = Files.createTempDirectory("update-eap");
            boolean prepared = false;
            try (BuildUpdateAction prepareUpdateAction = new BuildUpdateAction(installDir, targetDir, mavenSessionManager, console, overrideRepositories)) {
                prepared = prepareUpdateAction.buildUpdate();
            }
            if (prepared) {
                try (ApplyUpdateAction applyUpdateAction = new ApplyUpdateAction(installDir, targetDir)) {
                    applyUpdateAction.applyUpdate();
                }
            }
        } catch (IOException e) {
            throw new ProvisioningException("Unable to create temporary directory", e);
        } catch (OperationException e) {
            throw new RuntimeException(e);
        } finally {
            if (targetDir != null) {
                FileUtils.deleteQuietly(targetDir.toFile());
            }
        }
    }

    public UpdateSet findUpdates() throws OperationException, ProvisioningException {
        Path targetDir = null;
        try {
            targetDir = Files.createTempDirectory("update-eap");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (BuildUpdateAction prepareUpdateAction = new BuildUpdateAction(installDir, targetDir, mavenSessionManager, console, overrideRepositories)) {
            return prepareUpdateAction.findUpdates();
        } finally {
            FileUtils.deleteQuietly(targetDir.toFile());
        }
    }

    @Override
    public void close() {
        metadata.close();
    }

    private ProsperoConfig addTemporaryRepositories(List<Repository> repositories) {
        final ProsperoConfig prosperoConfig = metadata.getProsperoConfig();

        final List<Channel> channels = TemporaryRepositoriesHandler.overrideRepositories(prosperoConfig.getChannels(), repositories);

        return new ProsperoConfig(channels);
    }
}
