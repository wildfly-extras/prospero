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

package org.wildfly.prospero.cli;


import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import org.jboss.galleon.ProvisioningException;
import org.wildfly.prospero.actions.ApplyPatchAction;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

public class ActionFactory {

    public ProvisioningAction install(Path targetPath, MavenSessionManager mavenSessionManager, Console console) {
        return new ProvisioningAction(targetPath, mavenSessionManager, console);
    }

    public UpdateAction update(Path targetPath, MavenSessionManager mavenSessionManager, Console console)
            throws OperationException,
            ProvisioningException {
        return new UpdateAction(targetPath, mavenSessionManager, console);
    }

    // Option for BETA update support
    // TODO: evaluate in GA - replace by repository:add / custom channels?
    public UpdateAction update(Path targetPath, MavenSessionManager mavenSessionManager, Console console, List<URL> additionalRepositories)
            throws OperationException,
            ProvisioningException {
        return new UpdateAction(targetPath, mavenSessionManager, console, additionalRepositories);
    }

    public InstallationHistoryAction history(Path targetPath, Console console) {
        return new InstallationHistoryAction(targetPath, console);
    }

    public MetadataAction metadataActions(Path targetPath) {
        return new MetadataAction(targetPath);
    }

    public ApplyPatchAction applyPatch(Path targetPath, MavenSessionManager mavenSessionManager, Console console) throws OperationException {
        return new ApplyPatchAction(targetPath, mavenSessionManager, console);
    }
}
