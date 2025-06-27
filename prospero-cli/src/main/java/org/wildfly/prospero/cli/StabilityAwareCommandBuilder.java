package org.wildfly.prospero.cli;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

import org.wildfly.prospero.DistributionInfo;
import org.wildfly.prospero.Stability;
import org.wildfly.prospero.StabilityLevel;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.AbstractParentCommand;
import picocli.CommandLine;

class StabilityAwareCommandBuilder {


    // Cache stability level per build operation to avoid repeated calls
    private volatile Stability currentStability;

    CommandLine build(BuildableCommand mainCommand) {
        Objects.requireNonNull(mainCommand);

        // Cache the stability level for this build operation (thread-safe)
        this.currentStability = DistributionInfo.getStability();

        final CommandLine.Model.CommandSpec mainSpec = removeRestrictedArguments(
            CommandLine.Model.CommandSpec.forAnnotatedObject(mainCommand)
        );

        final CommandLine commandLine = new CommandLine(mainSpec);
        commandLine.setCommandName(DistributionInfo.DIST_NAME);

        processSubcommands(mainSpec);

        return commandLine;
    }

    private void processSubcommands(CommandLine.Model.CommandSpec mainSpec) {
        final Stack<CommandLine.Model.CommandSpec> commands = new Stack<>();
        commands.push(mainSpec);

        while (!commands.isEmpty()) {
            final CommandLine.Model.CommandSpec parentSpec = commands.pop();

            // Safe cast with validation
            if (!(parentSpec.userObject() instanceof AbstractParentCommand)) {
                continue; // Skip non-parent commands
            }

            final AbstractParentCommand parent = (AbstractParentCommand) parentSpec.userObject();
            final List<AbstractCommand> subcommands = parent.getSubcommands();

            if (subcommands == null) {
                continue; // Skip if no subcommands
            }

            for (AbstractCommand subcommand : subcommands) {
                if (subcommand == null) {
                    throw new IllegalStateException("Found null subcommand in " +
                        parent.getClass().getSimpleName());
                }

                if (isCommandPermitted(subcommand)) {
                    final CommandLine.Model.CommandSpec spec = removeRestrictedArguments(
                        CommandLine.Model.CommandSpec.forAnnotatedObject(subcommand)
                    );
                    parentSpec.addSubcommand(spec.name(), spec);

                    if (subcommand instanceof AbstractParentCommand) {
                        commands.push(spec);
                    }
                }
            }
        }
    }

    private boolean isCommandPermitted(AbstractCommand command) {
        if (command == null) {
            return false;
        }

        final StabilityLevel annotation = command.getClass().getAnnotation(StabilityLevel.class);
        final Stability level = annotation != null ? annotation.level() : Stability.Default;
        return currentStability.permits(level);
    }

    private CommandLine.Model.CommandSpec removeRestrictedArguments(CommandLine.Model.CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec);

        final ArrayList<CommandLine.Model.OptionSpec> toRemove = new ArrayList<>();

        for (CommandLine.Model.OptionSpec option : commandSpec.options()) {
            if (option == null) {
                continue; // Skip null options
            }

            if (!isOptionPermitted(option)) {
                toRemove.add(option);
            }
        }

        CommandLine.Model.CommandSpec result = commandSpec;
        for (CommandLine.Model.OptionSpec optionSpec : toRemove) {
            result = result.remove(optionSpec);
        }
        return result;
    }

    private boolean isOptionPermitted(CommandLine.Model.OptionSpec option) {
        if (option == null || option.userObject() == null) {
            return true; // Include by default if we can't determine
        }

        // Safe cast with validation
        if (!(option.userObject() instanceof Field)) {
            return true; // Include non-field options by default
        }

        final Field field = (Field) option.userObject();
        final StabilityLevel annotation = field.getAnnotation(StabilityLevel.class);
        final Stability level = annotation != null ? annotation.level() : Stability.Default;
        return currentStability.permits(level);
    }
}
