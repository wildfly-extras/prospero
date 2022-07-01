package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataActions;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.model.RepositoryRef;
import picocli.CommandLine;

@CommandLine.Command(name = CliConstants.LIST)
public class RepositoryListCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.DIR, required = true)
    Path directory;

    public RepositoryListCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        MetadataActions metadataActions = actionFactory.metadataActions(directory);
        List<RepositoryRef> repositories = metadataActions.getRepositories();

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
