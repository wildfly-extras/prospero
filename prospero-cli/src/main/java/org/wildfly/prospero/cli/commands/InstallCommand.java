/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.wildfly.channel.Channel;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.KnownFeaturePacks;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.options.LocalRepoOptions;
import org.wildfly.prospero.cli.commands.options.FeaturePackCandidates;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.Commands.INSTALL,
        sortOptions = false
)
public class InstallCommand extends AbstractCommand {

    @CommandLine.ArgGroup(
            heading = "%nInstallation source:%n",
            exclusive = true,
            multiplicity = "1",
            order = 1
    )
    FeaturePackOrDefinition featurePackOrDefinition;

        @CommandLine.Option(
                names = CliConstants.DIR,
                required = true,
                order = 2
        )
        Path directory;

        @CommandLine.Option(
                names = CliConstants.CHANNELS,
                paramLabel = CliConstants.CHANNEL_REFERENCE,
                order = 3
        )
        List<String> channelCoordinates = new ArrayList<>();

        @CommandLine.Option(
                names = CliConstants.CHANNEL_MANIFEST,
                paramLabel = CliConstants.CHANNEL_MANIFEST_REFERENCE,
                order = 4
        )
        Optional<String> manifestCoordinate;

        @CommandLine.Option(
                names = CliConstants.REPOSITORIES,
                paramLabel = CliConstants.REPO_URL,
                split = ",",
                order = 5
        )
        List<String> remoteRepositories = new ArrayList<>();

        @CommandLine.ArgGroup(exclusive = true, order = 6, headingKey = "localRepoOptions.heading")
        LocalRepoOptions localRepoOptions;

        @CommandLine.Option(
                names = CliConstants.OFFLINE,
                order = 8
        )
        boolean offline;

    static class FeaturePackOrDefinition {
        @CommandLine.Option(
                names = CliConstants.FPL,
                paramLabel = CliConstants.FEATURE_PACK_REFERENCE,
                completionCandidates = FeaturePackCandidates.class,
                required = true,
                order = 1
        )
        Optional<String> fpl;

        @CommandLine.Option(
                names = CliConstants.DEFINITION,
                paramLabel = CliConstants.PATH,
                required = true,
                order = 2
        )
        Optional<Path> definition;
    }

    public InstallCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        final long startTime = System.currentTimeMillis();

        // following is checked by picocli, adding this to avoid IDE warnings
        assert featurePackOrDefinition.definition.isPresent() || featurePackOrDefinition.fpl.isPresent();
        if (featurePackOrDefinition.definition.isEmpty() && isStandardFpl(featurePackOrDefinition.fpl.get())
                && channelCoordinates.isEmpty() && manifestCoordinate.isEmpty()) {
            throw CliMessages.MESSAGES.channelsMandatoryWhenCustomFpl();
        }

        if (!channelCoordinates.isEmpty() && manifestCoordinate.isPresent()) {
            throw CliMessages.MESSAGES.exclusiveOptions(CliConstants.CHANNELS, CliConstants.CHANNEL_MANIFEST);
        }

        final Optional<Path> localMavenCache = LocalRepoOptions.getLocalMavenCache(localRepoOptions);

        final MavenSessionManager mavenSessionManager = new MavenSessionManager(localMavenCache, offline);

        final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
                .setFpl(featurePackOrDefinition.fpl.orElse(null))
                .setManifest(manifestCoordinate.orElse(null))
                .setChannelCoordinates(channelCoordinates)
                .setOverrideRepositories(RepositoryDefinition.from(remoteRepositories))
                .setDefinitionFile(featurePackOrDefinition.definition.map(Path::toUri).orElse(null))
                .build();

        final VersionResolverFactory versionResolverFactory = createVersionResolverFactory(mavenSessionManager);
        final List<Channel> channels = provisioningDefinition.resolveChannels(versionResolverFactory);

        ProvisioningAction provisioningAction = actionFactory.install(directory.toAbsolutePath(), mavenSessionManager,
                console);
        provisioningAction.provision(provisioningDefinition.toProvisioningConfig(), channels);

        final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
        console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

        return ReturnCodes.SUCCESS;
    }

    private static VersionResolverFactory createVersionResolverFactory(MavenSessionManager mavenSessionManager) {
        final RepositorySystem repositorySystem = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession repositorySystemSession = mavenSessionManager.newRepositorySystemSession(
                repositorySystem);
        return new VersionResolverFactory(repositorySystem, repositorySystemSession);
    }

    private boolean isStandardFpl(String fpl) {
        return !KnownFeaturePacks.isWellKnownName(fpl);
    }

}
