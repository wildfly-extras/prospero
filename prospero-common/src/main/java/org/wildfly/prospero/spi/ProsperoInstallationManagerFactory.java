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

import org.jboss.galleon.Constants;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;
import org.wildfly.prospero.DistributionInfo;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.stability.Stability;
import org.wildfly.prospero.VersionLogger;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.stability.StabilityAwareInvocationHandler;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ProsperoInstallationManagerFactory implements InstallationManagerFactory {

    static {
        VersionLogger.logVersionOnStartup();
    }

    protected static final List<Path> REQUIRED_FILES = List.of(
            Path.of(Constants.PROVISIONED_STATE_DIR, Constants.PROVISIONING_XML),
            Path.of(ProsperoMetadataUtils.METADATA_DIR, ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME),
            Path.of(ProsperoMetadataUtils.METADATA_DIR, ProsperoMetadataUtils.MANIFEST_FILE_NAME)
    );

    // TODO: use stability from the API
    public InstallationManager create(Path installationDir, MavenOptions mavenOptions, Stability stability) throws Exception {
        DistributionInfo.setStability(stability);

        return create(installationDir, mavenOptions);
    }

    @Override
    public InstallationManager create(Path installationDir, MavenOptions mavenOptions) throws Exception {
        verifyInstallationDirectory(installationDir);
        final ProsperoInstallationManager pim = new ProsperoInstallationManager(installationDir, mavenOptions);
        final Object proxy = Proxy.newProxyInstance(ProsperoInstallationManager.class.getClassLoader(), new Class[]{InstallationManager.class}, new StabilityAwareInvocationHandler(pim));
        return (InstallationManager) proxy;
    }

    @Override
    public String getName() {
        return "prospero";
    }

    private void verifyInstallationDirectory(Path path) {
        final List<Path> missingPaths = REQUIRED_FILES.stream()
                .map(path::resolve)
                .filter(p->!p.toFile().isFile()).
                collect(Collectors.toList());

        if (!missingPaths.isEmpty()) {
            throw ProsperoLogger.ROOT_LOGGER.invalidInstallationDir(path, missingPaths);
        }
    }

}
