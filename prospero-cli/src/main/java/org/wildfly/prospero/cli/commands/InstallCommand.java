package org.wildfly.prospero.cli.commands;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.galleon.ProvisioningException;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.Provision;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.WellKnownFeaturePacks;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMain;
import org.wildfly.prospero.cli.Messages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.INSTALL,
        description = "Installs a new application server instance.",
        sortOptions = false
)
public class InstallCommand extends AbstractCommand {

    @CommandLine.Option(
            names = CliConstants.DIR,
            required = true,
            description = "Target directory where the application server is going to be provisioned."
    )
    Path directory;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    FeaturePackOrDefinition featurePackOrDefinition;

    @CommandLine.Option(
            names = CliConstants.CHANNEL,
            description = "Channel file URL."
    )
    Optional<String> channel;

    @CommandLine.Option(
            names = CliConstants.PROVISION_CONFIG,
            description = "Provisioning configuration file path. This is special JSON configuration file that cotnains list of channel file references and list of remote Maven repositories."
    )
    Optional<Path> provisionConfig;

    @CommandLine.Option(
            names = CliConstants.CHANNEL_REPO,
            description = "URL of a remote Maven repository that contains artifacts required to build an application container."
    )
    List<URL> channelRepositories;

    @CommandLine.Option(
            names = CliConstants.LOCAL_REPO,
            // TODO: Fix description.
            description = "Path to a local Maven repository."
    )
    Optional<Path> localRepo;

    @CommandLine.Option(
            names = CliConstants.OFFLINE,
            description = "Perform installation from local Maven repository only. Offline installation requires --local-repo to be configured."
    )
    boolean offline;

    @CommandLine.Option(names = {CliConstants.H, CliConstants.HELP}, usageHelp = true)
    boolean help;

    static class FeaturePackOrDefinition {
        @CommandLine.Option(
                names = CliConstants.FPL,
                required = true,
                // TODO: Check what the correct notations of fpl are.
                description = "Feature pack location. This can be a well known shortcut like \"eap-8.0\" or \"wildfly\", " +
                        "or a fully qualified feature pack location like " +
                        "\"wildfly-core@maven(org.jboss.universe:community-universe):current\"."
        )
        Optional<String> fpl;

        @CommandLine.Option(
                names = CliConstants.DEFINITION,
                required = true,
                description = "Galleon provisioning XML definition file path."
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
            console.error(Messages.provisioningConfigMandatoryWhenCustomFpl(), CliMain.PROVISION_CONFIG_ARG);
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        if (offline && localRepo.isEmpty()) {
            console.error(Messages.offlineModeRequiresLocalRepo());
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        try {
            final MavenSessionManager mavenSessionManager;
            if (localRepo.isEmpty()) {
                mavenSessionManager = new MavenSessionManager();
            } else {
                mavenSessionManager = new MavenSessionManager(localRepo.get().toAbsolutePath());
            }
            mavenSessionManager.setOffline(offline);

            final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
                    .setFpl(featurePackOrDefinition.fpl.orElse(null))
                    .setChannel(channel.orElse(null))
                    .setProvisionConfig(provisionConfig.orElse(null))
                    .setChannelRepo(channelRepositories == null || channelRepositories.isEmpty() ?
                            null : channelRepositories.get(0).toString())
                    .setDefinitionFile(featurePackOrDefinition.definition.orElse(null))
                    .build();

            Provision provision = actionFactory.install(directory.toAbsolutePath(), mavenSessionManager, console);
            provision.provision(provisioningDefinition);
        } catch (ProvisioningException | OperationException e) {
            console.error("Error while executing installation: " + e.getMessage());
            return ReturnCodes.PROCESSING_ERROR;
        }

        return ReturnCodes.SUCCESS;
    }

    private boolean isStandardFpl(String fpl) {
        return !WellKnownFeaturePacks.isWellKnownName(fpl);
    }
}
