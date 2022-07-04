package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.util.Optional;

import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.Logger;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.REVERT,
        sortOptions = false
)
public class RevertCommand extends AbstractCommand {

    private final Logger logger = Logger.getLogger(this.getClass());

    @CommandLine.Option(names = CliConstants.DIR, required = true)
    Path directory;

    @CommandLine.Option(names = CliConstants.REVISION, required = true)
    String revision;

    @CommandLine.Option(names = CliConstants.LOCAL_REPO)
    Optional<Path> localRepo;

    @CommandLine.Option(names = CliConstants.OFFLINE)
    boolean offline;

    public RevertCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        if (offline && localRepo.isEmpty()) {
            console.error(CliMessages.MESSAGES.offlineModeRequiresLocalRepo());
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        try {
            final MavenSessionManager mavenSessionManager = new MavenSessionManager(localRepo, offline);

            InstallationHistoryAction historyAction = actionFactory.history(directory.toAbsolutePath(), console);
            historyAction.rollback(new SavedState(revision), mavenSessionManager);
        } catch (ProvisioningException e) {
            console.error(CliMessages.MESSAGES.errorWhileExecutingOperation(CliConstants.REVERT, e.getMessage()));
            logger.error(CliMessages.MESSAGES.errorWhileExecutingOperation(CliConstants.INSTALL, e.getMessage()), e);
            return ReturnCodes.PROCESSING_ERROR;
        }

        return ReturnCodes.SUCCESS;
    }
}
