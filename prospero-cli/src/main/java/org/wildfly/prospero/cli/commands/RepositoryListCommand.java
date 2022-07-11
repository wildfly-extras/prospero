package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.model.RepositoryRef;
import picocli.CommandLine;

@CommandLine.Command(name = CliConstants.LIST)
public class RepositoryListCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    public RepositoryListCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        Path installationDirectory = determineInstallationDirectory(directory);
        MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory);
        List<RepositoryRef> repositories = metadataAction.getRepositories();

        // calculate maximum length of repository id strings, to make the list nicely alligned
        int maxRepoIdLength = repositories.stream()
                .map(r -> r.getId().length())
                .max(Integer::compareTo)
                .orElse(0);

        for (RepositoryRef repo: repositories) {
            console.println("%s\t%s", StringUtils.rightPad(repo.getId(), maxRepoIdLength), repo.getUrl());
        }
        return ReturnCodes.SUCCESS;
    }
}
