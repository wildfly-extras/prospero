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
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.jboss.galleon.spec.ConfigLayerDependency;
import org.jboss.galleon.spec.ConfigLayerSpec;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.ChannelMetadataCoordinate;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.InvalidChannelMetadataException;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.ChannelDefinitionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.api.exceptions.UnresolvedChannelMetadataException;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import org.wildfly.prospero.galleon.FeaturePackLocationParser;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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

    public void addFeaturePack(String fplGA, Set<String> layers, String model, String configName) throws ProvisioningException, OperationException {
        FeaturePackLocation fpl = FeaturePackLocationParser.resolveFpl(fplGA);

        final String selectedConfig;
        final String selectedModel;

        try {
            final Map<String, Map<String, Set<String>>> allLayers = getAllLayers(fpl);

            if (allLayers.isEmpty()) {
                throw new ProvisioningException("No layers found in the configuration.");
            }

            if (model == null || model.isEmpty()) {
                if (allLayers.size() > 1) {
                    throw new ProvisioningException("Multiple models available, please choose one.");
                }
                selectedModel = allLayers.keySet().iterator().next();
            } else {
                if (!allLayers.containsKey(model)) {
                    throw new ProvisioningException("The model " + model + " is not available.");
                }
                selectedModel = model;
            }


            selectedConfig = configName == null ? selectedModel + ".xml" : configName;

            final Map<String, Set<String>> modelLayers = allLayers.get(selectedModel);
            for (String layer : layers) {
                if (!modelLayers.containsKey(layer)) {
                    throw new ProvisioningException("Unknown layer: " + layer);
                }
                // TODO: verify the layer is not already included - check in current configuration
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final ProvisioningConfig newConfig;
        try (GalleonEnvironment galleonEnv = getGalleonEnv(installDir);
            ProvisioningManager pm = galleonEnv.getProvisioningManager()) {
            final ProvisioningConfig existingConfig = pm.getProvisioningConfig();
            final ProvisioningConfig.Builder builder = ProvisioningConfig.builder(existingConfig);

            ConfigModel.Builder configBuilder;
            ConfigId id = new ConfigId(selectedModel, selectedConfig);
            if (existingConfig.hasDefinedConfig(id)) {
                ConfigModel cmodel = existingConfig.getDefinedConfig(id);
                configBuilder = ConfigModel.builder(cmodel);
                for (String layer: layers) {
                    if (cmodel.getExcludedLayers().contains(layer)){
                        configBuilder.removeExcludedLayer(layer);
                    }
                    if (!cmodel.getIncludedLayers().contains(layer)) {
                        configBuilder.includeLayer(layer);
                    }
                }
                builder.removeConfig(id);
            } else {
                configBuilder = ConfigModel.builder(selectedModel, selectedConfig);
                for (String layer: layers) {
                    configBuilder.includeLayer(layer);
                }
            }
            FeaturePackConfig.Builder fpBuilder;
            if (existingConfig.hasFeaturePackDep(fpl.getProducer())) {
                FeaturePackConfig fp = existingConfig.getFeaturePackDep(fpl.getProducer());
                fpBuilder = FeaturePackConfig.builder(fp);
                builder.removeFeaturePackDep(fp.getLocation());
            } else {
                fpBuilder = FeaturePackConfig.builder(fpl)
                        .setInheritConfigs(false)
                        .setInheritPackages(false);
            }

            newConfig = builder
                    .addConfig(configBuilder.build())
                    .addFeaturePackDep(fpBuilder.build())
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

    private ProvisioningConfig getExistingConfiguration(Path installDir) throws ProvisioningException,
            ChannelDefinitionException, MetadataException, UnresolvedChannelMetadataException {
        final MavenSessionManager msm = new MavenSessionManager();
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system);

        var factory = new VersionResolverFactory(system, session);

        var channelSession = initChannelSession(session, factory);

        MavenRepoManager repositoryManager = new ChannelMavenArtifactRepositoryManager(channelSession);
        return GalleonUtils.getProvisioningManager(installDir, repositoryManager, (s)->{}).getProvisioningConfig();
    }

    private GalleonEnvironment getGalleonEnv(Path target) throws ProvisioningException, OperationException {
        return GalleonEnvironment
                .builder(target, prosperoConfig.getChannels(), mavenSessionManager)
                .setSourceServerPath(this.installDir)
                .setConsole(console)
                .build();
    }

    private Map<String, Map<String, Set<String>>> getAllLayers(FeaturePackLocation fpl) throws ProvisioningException, ChannelDefinitionException, UnresolvedChannelMetadataException, MetadataException, IOException {
        final ProvisioningConfig config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackConfig.builder(fpl).build())
                .build();

        final MavenSessionManager msm = new MavenSessionManager();
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system);

        var factory = new VersionResolverFactory(system, session);

        var channelSession = initChannelSession(session, factory);

        MavenRepoManager repositoryManager = new ChannelMavenArtifactRepositoryManager(channelSession);
        final ProvisioningLayoutFactory layoutFactory = GalleonUtils.getProvisioningLayoutFactory(repositoryManager);

        final ProvisioningLayout<FeaturePackLayout> layout = layoutFactory.newConfigLayout(config);

        Map<String, Map<String, Set<String>>> layersMap = new HashMap<>();
        for (FeaturePackLayout fp : layout.getOrderedFeaturePacks()) {
            for (ConfigId layer : fp.loadLayers()) {
                final String model = layer.getModel();
                Map<String, Set<String>> names = layersMap.get(model);
                if (names == null) {
                    names = new HashMap<>();
                    layersMap.put(model, names);
                }
                Set<String> dependencies = new TreeSet<>();
                ConfigLayerSpec spec = fp.loadConfigLayerSpec(model, layer.getName());
                for (ConfigLayerDependency dep : spec.getLayerDeps()) {
                    dependencies.add(dep.getName());
                }
                // Case where a layer is redefined in multiple FP. Add all deps.
                Set<String> existingDependencies = names.get(layer.getName());
                if(existingDependencies != null) {
                    existingDependencies.addAll(dependencies);
                    dependencies = existingDependencies;
                }
                names.put(layer.getName(), dependencies);
            }
        }

        return layersMap;
    }

    private ChannelSession initChannelSession(DefaultRepositorySystemSession session, MavenVersionsResolver.Factory factory) throws UnresolvedChannelMetadataException, ChannelDefinitionException, MetadataException {
        final ChannelSession channelSession;
        try {
            final ProsperoConfig prosperoConfig = ProsperoConfig.readConfig(installDir.resolve(ProsperoMetadataUtils.METADATA_DIR));
            channelSession = new ChannelSession(prosperoConfig.getChannels(), factory);
        } catch (UnresolvedMavenArtifactException e) {
            final Set<ChannelMetadataCoordinate> missingArtifacts = e.getUnresolvedArtifacts().stream()
                    .map(a -> new ChannelMetadataCoordinate(a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getClassifier(), a.getExtension()))
                    .collect(Collectors.toSet());

            throw new UnresolvedChannelMetadataException(ProsperoLogger.ROOT_LOGGER.unableToResolve(), e, missingArtifacts,
                    e.getAttemptedRepositories(), session.isOffline());
        } catch (InvalidChannelMetadataException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                final String url = e.getValidationMessages().get(0);
                try {
                    final ChannelMetadataCoordinate coord = new ChannelMetadataCoordinate(new URL(url));
                    throw new UnresolvedChannelMetadataException(ProsperoLogger.ROOT_LOGGER.unableToResolve(), e, Set.of(coord), Collections.emptySet(), session.isOffline());
                } catch (MalformedURLException ex) {
                    throw ProsperoLogger.ROOT_LOGGER.invalidManifest(e);
                }
            }
            throw ProsperoLogger.ROOT_LOGGER.invalidManifest(e);
        }
        return channelSession;
    }
}
