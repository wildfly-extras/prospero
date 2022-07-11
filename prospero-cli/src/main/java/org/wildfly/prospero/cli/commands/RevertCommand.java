package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.util.Optional;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.options.LocalRepoOptions;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.REVERT,
        sortOptions = false
)
public class RevertCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    @CommandLine.Option(names = CliConstants.REVISION, required = true)
    String revision;

    @CommandLine.ArgGroup(exclusive = true)
    LocalRepoOptions localRepoOptions;

    @CommandLine.Option(names = CliConstants.OFFLINE)
    boolean offline;

    public RevertCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        final Path installationDirectory = determineInstallationDirectory(directory);
        final MavenSessionManager mavenSessionManager = new MavenSessionManager(LocalRepoOptions.getLocalRepo(localRepoOptions), offline);

        InstallationHistoryAction historyAction = actionFactory.history(installationDirectory, console);
        historyAction.rollback(new SavedState(revision), mavenSessionManager);
        return ReturnCodes.SUCCESS;
    }
}
