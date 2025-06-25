package org.wildfly.prospero.cli;

import org.wildfly.prospero.cli.commands.AbstractCommand;

import java.util.List;

/**
 * Interface for commands that can be built by StabilityAwareCommandBuilder.
 * This allows testing with custom implementations while keeping the real MainCommand intact.
 */
public interface BuildableCommand {
    /**
     * Returns the list of subcommands for this command.
     * @return list of subcommands
     */
    List<AbstractCommand> getSubcommands();
}