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

package org.wildfly.prospero.cli;

import java.nio.file.Path;
import java.util.List;

import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.actions.BuildUpdateAction;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationExportAction;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.actions.InstallationRestoreAction;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.actions.PromoteArtifactBundleAction;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

public class ActionFactory {

    public ProvisioningAction install(Path targetPath, MavenSessionManager mavenSessionManager, Console console) {
        return new ProvisioningAction(targetPath, mavenSessionManager, console);
    }

    // Option for BETA update support
    // TODO: evaluate in GA - replace by repository:add / custom channels?
    public UpdateAction update(Path targetPath, MavenSessionManager mavenSessionManager, Console console, List<Repository> additionalRepositories)
            throws OperationException,
            ProvisioningException {
        return new UpdateAction(targetPath, mavenSessionManager, console, additionalRepositories);
    }

    public BuildUpdateAction buildUpdate(Path installationPath, Path targetPath, MavenSessionManager mavenSessionManager, Console console, List<Repository> additionalRepositories)
            throws OperationException,
            ProvisioningException {
        return new BuildUpdateAction(installationPath, targetPath, mavenSessionManager, console, additionalRepositories);
    }

    public InstallationHistoryAction history(Path targetPath, Console console) {
        return new InstallationHistoryAction(targetPath, console);
    }

    public MetadataAction metadataActions(Path targetPath) throws MetadataException {
        return new MetadataAction(targetPath);
    }

    public PromoteArtifactBundleAction promoter(Console console) {
        return new PromoteArtifactBundleAction(console);
    }

    public InstallationExportAction exportAction(Path targetPath) {
        return new InstallationExportAction(targetPath);
    }

    public InstallationRestoreAction restoreAction(Path targetPath, MavenSessionManager mavenSessionManager, Console console) {
        return new InstallationRestoreAction(targetPath, mavenSessionManager, console);
    }
}
