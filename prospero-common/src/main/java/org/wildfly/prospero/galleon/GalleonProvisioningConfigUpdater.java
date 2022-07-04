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

package org.wildfly.prospero.galleon;

import org.wildfly.prospero.api.exceptions.OperationException;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;

public class GalleonProvisioningConfigUpdater {

    private final MavenRepoManager mavenRepoManager;

    public GalleonProvisioningConfigUpdater(MavenRepoManager mavenRepoManager) {
        this.mavenRepoManager = mavenRepoManager;
    }

    public ProvisioningConfig updateFPs(ProvisioningConfig oldConfig) throws OperationException {
        try {
            final ProvisioningConfig.Builder builder = ProvisioningConfig.builder();
            for (FeaturePackConfig oldFpConfig : oldConfig.getFeaturePackDeps()) {
                final FeaturePackConfig newConfig = updateFeaturePackConfig(oldFpConfig);

                builder.addFeaturePackDep(newConfig);
            }

            builder.addOptions(oldConfig.getOptions());
            return builder.build();
        } catch (ProvisioningDescriptionException | MavenUniverseException e) {
            throw new OperationException("Unable to update provisioning definition", e);
        }
    }

    private FeaturePackConfig updateFeaturePackConfig(FeaturePackConfig oldFpConfig) throws MavenUniverseException, ProvisioningDescriptionException {
        final FeaturePackLocation fpl = oldFpConfig.getLocation();
        if (!fpl.isMavenCoordinates()) {
            return oldFpConfig;

        }
        final MavenArtifact artifact = parseToArtifact(fpl);
        artifact.setVersion(mavenRepoManager.getLatestVersion(artifact));

        return cloneConfig(oldFpConfig, artifact);
    }

    private FeaturePackConfig cloneConfig(FeaturePackConfig featurePackDep, MavenArtifact artifact) throws ProvisioningDescriptionException {
        final FeaturePackConfig.Builder newConfig = FeaturePackConfig
                .builder(new FeaturePackLocation(UniverseSpec.fromString("maven"), artifact.getGroupId() + ":" + artifact.getArtifactId() + "::zip",
                        null, null, artifact.getVersion()));
        if (featurePackDep.getInheritPackages() != null) {
            newConfig.setInheritPackages(featurePackDep.getInheritPackages());
        }
        if (featurePackDep.getInheritConfigs() != null) {
            newConfig.setInheritConfigs(featurePackDep.getInheritConfigs());
        }
        newConfig.includeAllPackages(featurePackDep.getIncludedPackages());
        newConfig.excludeAllPackages(featurePackDep.getExcludedPackages());
        return newConfig.build();
    }

    private MavenArtifact parseToArtifact(FeaturePackLocation fpl) {
        final String producerName = fpl.getProducerName();
        final String[] split = producerName.split(":");
        final MavenArtifact artifact = new MavenArtifact();
        artifact.setGroupId(split[0]);
        artifact.setArtifactId(split[1]);
        artifact.setVersion(split[2]);
        artifact.setExtension(split[3]);
        return artifact;
    }
}
