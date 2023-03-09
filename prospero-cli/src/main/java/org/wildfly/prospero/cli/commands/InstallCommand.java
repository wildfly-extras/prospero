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
import java.util.List;
import java.util.Optional;

import org.jboss.galleon.config.ProvisioningConfig;
import org.wildfly.channel.Channel;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.KnownFeaturePacks;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.LicensePrinter;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.options.FeaturePackCandidates;
import org.wildfly.prospero.licenses.License;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.Commands.INSTALL,
        sortOptions = false
)
public class InstallCommand extends AbstractInstallCommand {

    @CommandLine.Option(
            names = CliConstants.DIR,
            required = true,
            order = 2
    )
    Path directory;

    @CommandLine.Option(
            names = CliConstants.ACCEPT_AGREEMENTS,
            order = 8
    )
    boolean acceptAgreements;

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

    public InstallCommand(CliConsole console, ActionFactory actionFactory) {
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

        verifyTargetDirectoryIsEmpty(directory);

        final ProvisioningDefinition provisioningDefinition = buildDefinition();
        final MavenOptions mavenOptions = getMavenOptions();
        final ProvisioningConfig provisioningConfig = provisioningDefinition.toProvisioningConfig();
        final List<Channel> channels = resolveChannels(provisioningDefinition, mavenOptions);

        final ProvisioningAction provisioningAction = actionFactory.install(directory.toAbsolutePath(), mavenOptions,
                console);

        final List<License> pendingLicenses = provisioningAction.getPendingLicenses(provisioningConfig, channels);
        if (!pendingLicenses.isEmpty()) {
            new LicensePrinter().print(pendingLicenses);
            System.out.println();
            if (acceptAgreements) {
                System.out.println(CliMessages.MESSAGES.agreementSkipped(CliConstants.ACCEPT_AGREEMENTS));
                System.out.println();
            } else {
                if (!console.confirm(CliMessages.MESSAGES.acceptAgreements(), "", CliMessages.MESSAGES.installationCancelled())) {
                    return ReturnCodes.PROCESSING_ERROR;
                }
            }
        }

        provisioningAction.provision(provisioningConfig, channels);

        final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
        console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

        return ReturnCodes.SUCCESS;
    }

    private boolean isStandardFpl(String fpl) {
        return !KnownFeaturePacks.isWellKnownName(fpl);
    }

}
