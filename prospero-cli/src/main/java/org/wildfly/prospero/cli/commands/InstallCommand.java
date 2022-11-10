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

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.KnownFeaturePacks;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
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
                names = CliConstants.PROVISION_CONFIG,
                paramLabel = CliConstants.PATH,
                order = 3
        )
        Optional<Path> provisionConfig;

        @CommandLine.Option(
                names = CliConstants.CHANNEL,
                paramLabel = CliConstants.CHANNEL_REFERENCE,
                order = 4
        )
        Optional<String> channel;

        @CommandLine.Option(
                names = CliConstants.REMOTE_REPOSITORIES,
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
        if (featurePackOrDefinition.definition.isEmpty() && isStandardFpl(featurePackOrDefinition.fpl.get()) && provisionConfig.isEmpty()) {
            throw CliMessages.MESSAGES.prosperoConfigMandatoryWhenCustomFpl();
        }

        if (provisionConfig.isPresent() && channel.isPresent()) {
            throw CliMessages.MESSAGES.exclusiveOptions(CliConstants.PROVISION_CONFIG, CliConstants.CHANNEL);
        }

        if (provisionConfig.isPresent() && !remoteRepositories.isEmpty()) {
            throw CliMessages.MESSAGES.exclusiveOptions(CliConstants.PROVISION_CONFIG, CliConstants.REMOTE_REPOSITORIES);
        }

        final Optional<Path> localMavenCache = LocalRepoOptions.getLocalMavenCache(localRepoOptions);

        final MavenSessionManager mavenSessionManager = new MavenSessionManager(localMavenCache, offline);

        final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
                .setFpl(featurePackOrDefinition.fpl.orElse(null))
                .setManifest(channel.orElse(null))
                .setProvisionConfig(provisionConfig.orElse(null))
                .setOverrideRepositories(remoteRepositories)
                .setDefinitionFile(featurePackOrDefinition.definition.map(Path::toUri).orElse(null))
                .build();

        ProvisioningAction provisioningAction = actionFactory.install(directory.toAbsolutePath(), mavenSessionManager,
                console);
        provisioningAction.provision(provisioningDefinition.toProvisioningConfig(), provisioningDefinition.getChannels());

        final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
        console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

        return ReturnCodes.SUCCESS;
    }

    private boolean isStandardFpl(String fpl) {
        return !KnownFeaturePacks.isWellKnownName(fpl);
    }

}
