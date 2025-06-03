package org.wildfly.prospero.cli.printers;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.wildfly.prospero.api.ChannelVersion;
import org.wildfly.prospero.api.ChannelVersionChange;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.updates.ChannelsUpdateResult;
import picocli.CommandLine;

public class ChannelVersionChangesPrinter {

    private final Console console;
    private static final String INDENT = "  ";
    private static final String ITEM_ELEM = "* ";


    public ChannelVersionChangesPrinter(Console console) {
        this.console = console;
    }

    public void printDowngrades(Collection<ChannelVersionChange> downgrades) {
        console.println(CliMessages.MESSAGES.channelDowngradeWarningHeader());
        final StringBuilder sb = new StringBuilder();
        for (ChannelVersionChange downgrade : downgrades) {
            sb.append(INDENT).append(ITEM_ELEM).append(downgrade.channelName()).append(": ");
            sb.append(downgrade.oldVersion().getPhysicalVersion());
            if (downgrade.oldVersion().getLogicalVersion() != null) {
                sb.append(" (").append(downgrade.oldVersion().getLogicalVersion()).append(")");
            }

            sb.append("  ->  ").append(downgrade.newVersion().getPhysicalVersion());
            if (downgrade.newVersion().getLogicalVersion() != null) {
                sb.append(" (").append(downgrade.oldVersion().getLogicalVersion()).append(")");
            }
            sb.append(System.lineSeparator());
        }
        console.println(sb.toString());
    }

    public void printUnexpectedDowngradesError(Collection<ChannelVersionChange> downgrades, CommandLine.Model.CommandSpec spec) {
        final StringBuilder versionArg = new StringBuilder();
        for (ChannelVersionChange downgrade : downgrades) {
            versionArg.append("--version=")
                    .append(downgrade.channelName())
                    .append("::")
                    .append(downgrade.newVersion().getPhysicalVersion())
                    .append(" ");
        }
        console.println(CliMessages.MESSAGES.unexpectedVersionsHeader(buildCommandString(spec) + versionArg));
    }

    public void printAvailableChannelChanges(ChannelsUpdateResult result, String dir) {
        if (!result.getUnsupportedChannels().isEmpty()) {
            console.println("Some of the channels the server is subscribed to do not support the versioned manifests. The server has to be subscribed only to Maven channels to use this feature.");
            return;
        }

        if (!result.hasUpdates()) {
            console.println("All the server channels are at the latest versions.");
            return;
        }

        final StringBuilder versionArg = new StringBuilder();
        console.println("Found new versions of channel manifests available:");
        for (String channelName : result.getUpdatedChannels()) {
            final Set<ChannelVersion> updatedVersion = result.getUpdatedVersion(channelName);
            if (!updatedVersion.isEmpty()) {
                versionArg.append(channelName).append("::").append(updatedVersion.iterator().next().getPhysicalVersion());

                console.println(" - channel-name: %s".formatted(channelName));
                console.println("   current-version: %s".formatted(result.getOriginalVersions(channelName)));
                console.println("   available-versions:");
                for (ChannelVersion channelVersion : updatedVersion) {
                    console.println("   - %s(%s)".formatted(channelVersion.getPhysicalVersion(), channelVersion.getLogicalVersion()));
                }
            } else {
                versionArg.append(channelName).append("::").append(result.getOriginalVersions(channelName));
            }
        }

        console.println("");
        console.println("To perform the update to selected version use update operation with --version parameter like:");
        console.println("  prospero update perform --dir " + dir + " --version " + versionArg.toString());
    }

    private String buildCommandString(CommandLine.Model.CommandSpec spec) {
        final List<String> args = spec.commandLine().getParseResult().originalArgs();
        final StringBuilder sb = new StringBuilder();

        sb.append(spec.root().name()).append(" ");
        args.forEach(a->sb.append(a).append(" "));

        return sb.toString().trim();
    }
}
