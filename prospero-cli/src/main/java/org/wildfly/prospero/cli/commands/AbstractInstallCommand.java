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

package org.wildfly.prospero.cli.commands;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.ProvisioningConfig;
import org.wildfly.channel.Channel;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ArgumentParsingException;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.LicensePrinter;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.options.LocalRepoOptions;
import org.wildfly.prospero.licenses.License;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AbstractInstallCommand extends AbstractCommand {

    @CommandLine.ArgGroup(
            heading = "%nInstallation source:%n",
            exclusive = true,
            multiplicity = "1",
            order = 1
    )
    InstallCommand.FeaturePackOrDefinition featurePackOrDefinition;

    @CommandLine.Option(
            names = {CliConstants.CHANNELS, CliConstants.CHANNEL},
            paramLabel = CliConstants.CHANNEL_REFERENCE,
            order = 2,
            split = ","
    )
    List<String> channelCoordinates = new ArrayList<>();

    @CommandLine.Option(
            names = CliConstants.CHANNEL_MANIFEST,
            paramLabel = CliConstants.CHANNEL_MANIFEST_REFERENCE,
            order = 3
    )
    Optional<String> manifestCoordinate;

    @CommandLine.Option(
            names = CliConstants.REPOSITORIES,
            paramLabel = CliConstants.REPO_URL,
            split = ",",
            order = 4
    )
    List<String> remoteRepositories = new ArrayList<>();

    @CommandLine.ArgGroup(exclusive = true, order = 5, headingKey = "localRepoOptions.heading")
    LocalRepoOptions localRepoOptions;

    @CommandLine.Option(
            names = CliConstants.OFFLINE,
            order = 6
    )
    boolean offline;

    public AbstractInstallCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {

        final Path tempDirectory = Files.createTempDirectory("eap-installer");
        try {
            final ProvisioningDefinition provisioningDefinition = buildDefinition();
            final MavenSessionManager mavenSessionManager = createMavenSession();
            final ProvisioningConfig provisioningConfig = provisioningDefinition.toProvisioningConfig();
            final VersionResolverFactory versionResolverFactory = InstallCommand.createVersionResolverFactory(mavenSessionManager);
            final List<Channel> channels = provisioningDefinition.resolveChannels(versionResolverFactory);

            final ProvisioningAction provisioningAction = actionFactory.install(tempDirectory.toAbsolutePath(), mavenSessionManager,
                    console);

            final List<License> pendingLicenses = provisioningAction.getPendingLicenses(provisioningConfig, channels);
            if (!pendingLicenses.isEmpty()) {
                System.out.println();
                System.out.println(CliMessages.MESSAGES.listAgreementsHeader());
                System.out.println();
                new LicensePrinter().print(pendingLicenses);
            } else {
                System.out.println();
                System.out.println(CliMessages.MESSAGES.noAgreementsNeeded());
            }
            return ReturnCodes.SUCCESS;
        } finally {
            tempDirectory.toFile().delete();
        }
    }

    protected MavenSessionManager createMavenSession() throws ArgumentParsingException, ProvisioningException {
        final Optional<Path> localMavenCache = LocalRepoOptions.getLocalMavenCache(localRepoOptions);

        final MavenSessionManager mavenSessionManager = createMavenSessionManager(localMavenCache);
        return mavenSessionManager;
    }

    protected ProvisioningDefinition buildDefinition() throws MetadataException, NoChannelException, ArgumentParsingException {
        final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
                .setFpl(featurePackOrDefinition.fpl.orElse(null))
                .setManifest(manifestCoordinate.orElse(null))
                .setChannelCoordinates(channelCoordinates)
                .setOverrideRepositories(RepositoryDefinition.from(remoteRepositories))
                .setDefinitionFile(featurePackOrDefinition.definition.map(Path::toUri).orElse(null))
                .build();
        return provisioningDefinition;
    }

    protected MavenSessionManager createMavenSessionManager(Optional<Path> localMavenCache) throws ProvisioningException {
        final MavenSessionManager mavenSessionManager = new MavenSessionManager(localMavenCache, offline);
        return mavenSessionManager;
    }

    protected static VersionResolverFactory createVersionResolverFactory(MavenSessionManager mavenSessionManager) {
        final RepositorySystem repositorySystem = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession repositorySystemSession = mavenSessionManager.newRepositorySystemSession(
                repositorySystem);
        return new VersionResolverFactory(repositorySystem, repositorySystemSession);
    }
}
