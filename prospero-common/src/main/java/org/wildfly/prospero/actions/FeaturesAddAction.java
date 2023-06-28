/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.FeaturePackLocationParser;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class FeaturesAddAction {

    private final MavenSessionManager mavenSessionManager;
    private final Path installDir;
    private final InstallationMetadata metadata;
    private final ProsperoConfig prosperoConfig;
    private final Console console;

    public FeaturesAddAction(MavenSessionManager mavenSessionManager, Path installDir, Console console) throws MetadataException {
        this.mavenSessionManager = mavenSessionManager;
        this.installDir = installDir;
        this.console = console;
        this.metadata = InstallationMetadata.loadInstallation(installDir);
        this.prosperoConfig = metadata.getProsperoConfig();
    }

    public void addFeaturePack(String fplGA, Set<String> layers, Set<String> names) throws ProvisioningException, OperationException {
        FeaturePackLocation fpl = FeaturePackLocationParser.resolveFpl(fplGA);

        // TODO: include all names
        // TODO: handle empty names
        final ConfigModel.Builder configModelBuilder = ConfigModel.builder()
                .setModel("standalone")
                .setName("standalone.xml");

        for (String layer : layers) {
            // TODO: validate layer exists
            configModelBuilder.includeLayer(layer);
        }

        FeaturePackConfig config = FeaturePackConfig.builder(fpl)
                .addConfig(configModelBuilder.build())
                .build();

        final ProvisioningConfig newConfig;
        try (GalleonEnvironment galleonEnv = getGalleonEnv(installDir);
            ProvisioningManager pm = galleonEnv.getProvisioningManager()) {
            final ProvisioningConfig existingConfig = pm.getProvisioningConfig();
            // TODO: see Galleon ProvisioningLayout#install - need to handle case of installing a transitive dep
            newConfig = ProvisioningConfig.builder(existingConfig)
                    .addFeaturePackDep(config)
                    .build();
        }

        Path candidate = null;
        try {
            candidate = Files.createTempDirectory("prospero-candidate").toAbsolutePath();
            FileUtils.forceDeleteOnExit(candidate.toFile());

            try (PrepareCandidateAction prepareCandidateAction = new PrepareCandidateAction(installDir, mavenSessionManager, prosperoConfig);
                 GalleonEnvironment galleonEnv = getGalleonEnv(candidate)) {
                prepareCandidateAction.buildCandidate(candidate, galleonEnv, ApplyCandidateAction.Type.FEATURE_ADD, newConfig);
            }

            final ApplyCandidateAction applyCandidateAction = new ApplyCandidateAction(installDir, candidate);
            applyCandidateAction.applyUpdate(ApplyCandidateAction.Type.FEATURE_ADD);
        } catch (IOException e) {
            if (candidate!=null) {
                try {
                    FileUtils.forceDelete(candidate.toFile());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            throw new RuntimeException(e);
        }
    }

    private GalleonEnvironment getGalleonEnv(Path target) throws ProvisioningException, OperationException {
        return GalleonEnvironment
                .builder(target, prosperoConfig.getChannels(), mavenSessionManager)
                .setSourceServerPath(this.installDir)
                .setConsole(console)
                .build();
    }
}
