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

package com.redhat.prospero.cli.actions;

import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.api.Channel;
import com.redhat.prospero.cli.GalleonProgressCallback;
import com.redhat.prospero.maven.MavenUtils;
import org.eclipse.aether.RepositorySystem;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.jboss.galleon.universe.FeaturePackLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class GalleonUtils {
    public static ChannelMavenArtifactRepositoryManager getChannelRepositoryManager(List<Channel> channels, RepositorySystem repoSystem) throws ProvisioningException, IOException {
        return new ChannelMavenArtifactRepositoryManager(repoSystem, MavenUtils.getDefaultRepositorySystemSession(repoSystem), channels);
    }

    public static ProvisioningManager getProvisioningManager(Path installDir, ChannelMavenArtifactRepositoryManager maven) throws ProvisioningException {
        ProvisioningManager provMgr = ProvisioningManager.builder().addArtifactResolver(maven)
                .setInstallationHome(installDir).build();
        addProgressCallbacks(provMgr.getLayoutFactory());
        return provMgr;
    }

    public static void addProgressCallbacks(ProvisioningLayoutFactory layoutFactory) {
        layoutFactory.setProgressCallback("LAYOUT_BUILD", new GalleonProgressCallback<FeaturePackLocation.FPID>("Resolving feature-pack", "Feature-packs resolved."));
        layoutFactory.setProgressCallback("PACKAGES", new GalleonProgressCallback<FeaturePackLocation.FPID>("Installing packages", "Packages installed."));
        layoutFactory.setProgressCallback("CONFIGS", new GalleonProgressCallback<FeaturePackLocation.FPID>("Generating configuration", "Configurations generated."));
        layoutFactory.setProgressCallback("JBMODULES", new GalleonProgressCallback<FeaturePackLocation.FPID>("Installing JBoss modules", "JBoss modules installed."));
    }
}
