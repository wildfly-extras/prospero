package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistory;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.HISTORY,
        description = "Lists previous installation states.",
        sortOptions = false
)
public class HistoryCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.DIR, required = true)
    Path directory;

    @CommandLine.Option(names = CliConstants.REVISION)
    Optional<String> revision;

    public HistoryCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        InstallationHistory installationHistory = actionFactory.history(directory.toAbsolutePath(), console);

        if (revision.isEmpty()) {
            List<SavedState> revisions = installationHistory.getRevisions();
            for (SavedState savedState : revisions) {
                console.println(savedState.shortDescription());
            }
        } else {
            List<ArtifactChange> changes = installationHistory.compare(new SavedState(revision.get()));
            if (changes.isEmpty()) {
                console.println("No changes found");
            } else {
                changes.forEach(c-> console.println(c.toString()));
            }
        }

        return ReturnCodes.SUCCESS;
    }
}
