package org.wildfly.prospero.cli.commands;

import java.net.URL;
import java.nio.file.Path;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

@CommandLine.Command(name = CliConstants.ADD)
public class RepositoryAddCommand extends AbstractCommand {

    @CommandLine.Parameters(index = "0")
    String repoId;

    @CommandLine.Parameters(index = "1")
    URL url;

    @CommandLine.Option(names = CliConstants.DIR, required = true)
    Path directory;

    public RepositoryAddCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        try {
            MetadataAction metadataAction = actionFactory.metadataActions(directory);
            metadataAction.addRepository(repoId, url);
            console.println(CliMessages.MESSAGES.repositoryAdded(repoId));
            return ReturnCodes.SUCCESS;
        } catch (IllegalArgumentException e) {
            console.error(e.getMessage());
            return ReturnCodes.INVALID_ARGUMENTS;
        }
    }
}
