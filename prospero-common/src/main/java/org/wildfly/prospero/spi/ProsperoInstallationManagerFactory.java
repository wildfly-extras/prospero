/*
 *
 *  * Copyright 2022 Red Hat, Inc. and/or its affiliates
 *  * and other contributors as indicated by the @author tags.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.wildfly.prospero.spi;

import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;

import java.io.File;
import java.nio.file.Path;

public class ProsperoInstallationManagerFactory implements InstallationManagerFactory {
    @Override
    public InstallationManager create(Path installationDir, MavenOptions mavenOptions) throws Exception {
        verifyInstallationDirectory(installationDir);
        return new ProsperoInstallationManager(installationDir, mavenOptions);
    }

    @Override
    public String getName() {
        return "prospero";
    }

    private void verifyInstallationDirectory(Path path) {
        File dotGalleonDir = path.resolve(InstallationMetadata.GALLEON_INSTALLATION_DIR).toFile();
        File channelsFile = path.resolve(ProsperoMetadataUtils.METADATA_DIR)
                .resolve(ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME).toFile();
        if (!dotGalleonDir.isDirectory() || !channelsFile.isFile()) {
            throw Messages.MESSAGES.invalidInstallationDir(path);
        }
    }
}
