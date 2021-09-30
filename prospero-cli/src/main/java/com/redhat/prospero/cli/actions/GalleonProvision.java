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

import com.redhat.prospero.ChannelMavenArtifactRepositoryManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.universe.FeaturePackLocation;

public class GalleonProvision {

    static {
        enableJBossLogManager();
    }

    private static void enableJBossLogManager() {
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        }
    }

    public static void main(String[] args) throws ProvisioningException, IOException {
        if (args.length < 3) {
            System.out.println("Not enough parameters. Need to provide FPL, output directory and channels file.");
            return;
        }
        final String fpl = args[0];
        final String base = args[1];
        final String channelsFile = args[2];

        new GalleonProvision().installFeaturePack(fpl, base, channelsFile);
    }

    public void installFeaturePack(String fpl, String path, String channelsFile) throws ProvisioningException, IOException {
        Path installDir = Paths.get(path);
        if (Files.exists(installDir)) {
            throw new ProvisioningException("Installation dir " + installDir + " already exists");
        }
        try (final ChannelMavenArtifactRepositoryManager maven
                = GalleonUtils.getChannelRepositoryManager(Paths.get(channelsFile), GalleonUtils.newRepositorySystem())) {
            ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, maven);
            FeaturePackLocation loc = FeaturePackLocation.fromString(fpl);
            provMgr.install(loc);
            maven.done(installDir);
        }
    }

}
