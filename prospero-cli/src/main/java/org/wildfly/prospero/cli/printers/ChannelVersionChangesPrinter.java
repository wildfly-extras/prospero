package org.wildfly.prospero.cli.printers;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.wildfly.prospero.DistributionInfo;
import org.wildfly.prospero.api.ChannelVersion;
import org.wildfly.prospero.api.ChannelVersionChange;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.commands.CliConstants;
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

    public void printAvailableChannelChanges(ChannelsUpdateResult result, String serverDir) {
        if (!result.getUnsupportedChannels().isEmpty()) {
            console.println(CliMessages.MESSAGES.channelVersionListUnsupportedChannelType());
            return;
        }

        if (!result.hasUpdates()) {
            console.println(CliMessages.MESSAGES.noChannelVersionUpdates());
            return;
        }

        final StringBuilder versionArg = new StringBuilder();
        console.println(CliMessages.MESSAGES.channelVersionUpdateListHeader());
        for (String channelName : result.getUpdatedChannels()) {
            final Set<ChannelVersion> updatedVersion = result.getUpdatedVersion(channelName);
            if (!updatedVersion.isEmpty()) {
                versionArg.append(channelName).append("::").append(updatedVersion.iterator().next().getPhysicalVersion());

                console.println(" - %s: %s".formatted(CliMessages.MESSAGES.channelVersionUpdateListChannelName(), channelName));
                console.println("   %s: %s".formatted(CliMessages.MESSAGES.channelVersionUpdateListCurrentVersion(), result.getOriginalVersions(channelName)));
                console.println("   %s:".formatted(CliMessages.MESSAGES.channelVersionUpdateListAvailableVersions()));
                for (ChannelVersion channelVersion : updatedVersion) {
                    console.println("   - %s(%s)".formatted(channelVersion.getPhysicalVersion(), channelVersion.getLogicalVersion()));
                }
            } else {
                versionArg.append(channelName).append("::").append(result.getOriginalVersions(channelName));
            }
        }

        console.println("");

        final String updateCmd = "  %s %s %s %s %s %s %s".formatted(
                DistributionInfo.DIST_NAME, CliConstants.Commands.UPDATE, CliConstants.Commands.PERFORM,
                CliConstants.DIR, serverDir, CliConstants.VERSION, versionArg.toString());
        console.println(CliMessages.MESSAGES.channelVersionUpdateListUpdateCommandSuggestion(updateCmd));
    }

    private String buildCommandString(CommandLine.Model.CommandSpec spec) {
        final List<String> args = spec.commandLine().getParseResult().originalArgs();
        final StringBuilder sb = new StringBuilder();

        sb.append(spec.root().name()).append(" ");
        args.forEach(a->sb.append(a).append(" "));

        return sb.toString().trim();
    }
}
