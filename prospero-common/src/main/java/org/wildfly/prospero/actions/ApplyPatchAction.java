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

import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.patch.Patch;
import org.wildfly.prospero.patch.PatchArchive;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.wildfly.prospero.patch.PatchArchive.PATCH_REPO_FOLDER;

public class ApplyPatchAction {
    public static final String PATCH_REPO_NAME = "patch-cache";
    public static final String PATCHES_FOLDER = ".patches";
    public static final Path PATCHES_REPO_PATH = Paths.get(PATCHES_FOLDER, PATCH_REPO_FOLDER);
    private final Path installDir;
    private final InstallationMetadata metadata;
    private final MavenSessionManager mavenSessionManager;
    private final Console console;

    public ApplyPatchAction(Path targetPath, MavenSessionManager mavenSessionManager, Console console) throws OperationException {
        this.installDir = targetPath;
        this.metadata = new InstallationMetadata(targetPath);
        this.mavenSessionManager = mavenSessionManager;
        this.console = console;
    }

    public void apply(Path patchArchivePath) throws OperationException, ProvisioningException {
        // TODO: verify archive

        try {
            final PatchArchive patchArchive = new PatchArchive(patchArchivePath);
            console.println(Messages.MESSAGES.installingPatch(patchArchive.getName()));

            // install patch locally
            final Patch patch = patchArchive.extract(installDir);

            // update config
            final ProsperoConfig prosperoConfig = metadata.getProsperoConfig();
            prosperoConfig.addChannel(new ChannelRef(null, patch.getChannelFileUrl().toString()));

            // add cached repository to config if not present
            prosperoConfig.addRepository(new RepositoryRef(PATCH_REPO_NAME, installDir.resolve(PATCHES_REPO_PATH).toUri().toURL().toString()));
            metadata.updateProsperoConfig(prosperoConfig);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MetadataException e) {
            e.printStackTrace();
        }

        reprovisionServer();
    }

    private void reprovisionServer() throws OperationException, ProvisioningException {
        // have to parse the config after the patch metadata was applied
        final GalleonEnvironment galleonEnv = GalleonEnvironment
                .builder(installDir, metadata.getProsperoConfig(), mavenSessionManager)
                .setConsole(console)
                .build();

        ProvisioningManager provMgr = galleonEnv.getProvisioningManager();
        GalleonUtils.executeGalleon(options -> provMgr.provision(provMgr.getProvisioningConfig(), options),
                mavenSessionManager.getProvisioningRepo().toAbsolutePath());

        metadata.setChannel(galleonEnv.getRepositoryManager().resolvedChannel());

        metadata.recordProvision();
    }
}
