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

package com.redhat.prospero.galleon.repository;

import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.galleon.repository.GalleonPackInspector;
import com.redhat.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LocalMavenRepositoryBuilder {

    private final Path modulesDir;
    private final MavenSessionManager mavenSessionManager;

    public LocalMavenRepositoryBuilder(MavenSessionManager mavenSessionManager, Path modulesDir) {
        this.mavenSessionManager = mavenSessionManager;
        this.modulesDir = modulesDir;
    }

    public void populateLocalRepo(InstallationMetadata metadata, ProvisioningManager provMgr) throws ProvisioningException {
        final GalleonPackInspector galleonPackInspector = new GalleonPackInspector(metadata, modulesDir);

        List<Path> resolvedInstalledPacks = resolveInstalledFeaturePacks(provMgr);

        final List<Artifact> installedArtifacts = galleonPackInspector.getAllInstalledArtifacts(resolvedInstalledPacks);

        final RepositorySystem repositorySystem = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession repositorySystemSession = mavenSessionManager.newRepositorySystemSession(repositorySystem, false);
        try {
            InstallRequest installRequest = new InstallRequest();
            for (Artifact artifact : installedArtifacts) {
                installRequest.addArtifact(artifact);
            }
            repositorySystem.install(repositorySystemSession, installRequest);
        } catch (InstallationException e) {
            throw new ProvisioningException(e);
        }
    }

    private List<Path> resolveInstalledFeaturePacks(ProvisioningManager provMgr) throws ProvisioningException {
        List<Path> res = new ArrayList<>();
        final ProvisioningConfig provisioningConfig = provMgr.getProvisioningConfig();
        for (FeaturePackConfig featurePackDep : provisioningConfig.getFeaturePackDeps()) {
            res.add(provMgr.getLayoutFactory().getUniverseResolver().resolve(featurePackDep.getLocation()));
        }
        return res;
    }
}
