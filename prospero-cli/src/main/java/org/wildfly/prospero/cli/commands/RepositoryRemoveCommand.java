package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataActions;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

@CommandLine.Command(name = CliConstants.REMOVE)
public class RepositoryRemoveCommand extends AbstractCommand {

    @CommandLine.Parameters(index = "0")
    String repoId;

    @CommandLine.Option(names = CliConstants.DIR, required = true)
    Path directory;

    public RepositoryRemoveCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        try {
            MetadataActions metadataActions = actionFactory.metadataActions(directory);
            metadataActions.removeRepository(repoId);
            console.println(CliMessages.MESSAGES.repositoryRemoved(repoId));
            return ReturnCodes.SUCCESS;
        } catch (IllegalArgumentException e) {
            console.error(e.getMessage());
            return ReturnCodes.INVALID_ARGUMENTS;
        }
    }
}
