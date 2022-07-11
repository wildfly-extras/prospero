package org.wildfly.prospero.cli.commands;

import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

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

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    public RepositoryAddCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        Path installationDirectory = determineInstallationDirectory(directory);
        MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory);
        metadataAction.addRepository(repoId, url);
        console.println(CliMessages.MESSAGES.repositoryAdded(repoId));
        return ReturnCodes.SUCCESS;
    }
}
