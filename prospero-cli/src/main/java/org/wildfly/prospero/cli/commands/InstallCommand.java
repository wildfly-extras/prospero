package org.wildfly.prospero.cli.commands;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.Logger;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.WellKnownFeaturePacks;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMain;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.INSTALL,
        sortOptions = false
)
public class InstallCommand extends AbstractCommand {

    private final Logger logger = Logger.getLogger(this.getClass());

    @CommandLine.Option(
            names = CliConstants.DIR,
            required = true,
            order = 1
    )
    Path directory;

    @CommandLine.ArgGroup(
            exclusive = true,
            multiplicity = "1",
            order = 2
    )
    FeaturePackOrDefinition featurePackOrDefinition;

    @CommandLine.Option(
            names = CliConstants.PROVISION_CONFIG,
            order = 3
    )
    Optional<Path> provisionConfig;

    @CommandLine.Option(
            names = CliConstants.CHANNEL,
            order = 4
    )
    Optional<String> channel;

    @CommandLine.Option(
            names = CliConstants.CHANNEL_REPO,
            order = 5
    )
    List<URL> channelRepositories;

    @CommandLine.Option(
            names = CliConstants.LOCAL_REPO,
            order = 6
    )
    Optional<Path> localRepo;

    @CommandLine.Option(
            names = CliConstants.OFFLINE,
            order = 7
    )
    boolean offline;

    static class FeaturePackOrDefinition {
        @CommandLine.Option(
                names = CliConstants.FPL,
                required = true,
                order = 2
        )
        Optional<String> fpl;

        @CommandLine.Option(
                names = CliConstants.DEFINITION,
                required = true,
                order = 3
        )
        Optional<Path> definition;
    }

    public InstallCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() {
        // following is checked by picocli, adding this to avoid IDE warnings
        assert featurePackOrDefinition.definition.isPresent() || featurePackOrDefinition.fpl.isPresent();

        if (featurePackOrDefinition.definition.isEmpty() && isStandardFpl(featurePackOrDefinition.fpl.get()) && provisionConfig.isEmpty()) {
            console.error(CliMessages.MESSAGES.provisioningConfigMandatoryWhenCustomFpl(), CliMain.PROVISION_CONFIG_ARG);
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        if (offline && localRepo.isEmpty()) {
            console.error(CliMessages.MESSAGES.offlineModeRequiresLocalRepo());
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        try {
            final MavenSessionManager mavenSessionManager = new MavenSessionManager(localRepo, offline);

            final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
                    .setFpl(featurePackOrDefinition.fpl.orElse(null))
                    .setChannel(channel.orElse(null))
                    .setProvisionConfig(provisionConfig.orElse(null))
                    .setChannelRepo(channelRepositories == null || channelRepositories.isEmpty() ?
                            null : channelRepositories.get(0).toString())
                    .setDefinitionFile(featurePackOrDefinition.definition.orElse(null))
                    .build();

            ProvisioningAction provisioningAction = actionFactory.install(directory.toAbsolutePath(), mavenSessionManager, console);
            provisioningAction.provision(provisioningDefinition);
        } catch (ProvisioningException | OperationException e) {
            console.error(CliMessages.MESSAGES.errorWhileExecutingOperation(CliConstants.INSTALL, e.getMessage()));
            logger.error(CliMessages.MESSAGES.errorWhileExecutingOperation(CliConstants.INSTALL, e.getMessage()), e);
            return ReturnCodes.PROCESSING_ERROR;
        } catch (IllegalArgumentException e) {
            console.error(e.getMessage());
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        return ReturnCodes.SUCCESS;
    }

    private boolean isStandardFpl(String fpl) {
        return !WellKnownFeaturePacks.isWellKnownName(fpl);
    }

}
