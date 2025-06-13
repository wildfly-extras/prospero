package org.wildfly.prospero.cli;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Stack;

import org.wildfly.prospero.DistributionInfo;
import org.wildfly.prospero.Stability;
import org.wildfly.prospero.StabilityLevel;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.AbstractParentCommand;
import org.wildfly.prospero.cli.commands.MainCommand;
import picocli.CommandLine;

class StabilityAwareCommandBuilder {

    CommandLine build(MainCommand mainCommand) {
        final CommandLine.Model.CommandSpec mainSpec = removeRestrictedArguments(CommandLine.Model.CommandSpec.forAnnotatedObject(mainCommand));

        final CommandLine commandLine = new CommandLine(mainSpec);
        commandLine.setCommandName(DistributionInfo.DIST_NAME);

        final Stack<CommandLine.Model.CommandSpec> commands = new Stack<>();
        commands.push(mainSpec);

        while (!commands.isEmpty()) {
            final CommandLine.Model.CommandSpec parentSpec = commands.pop();
            final AbstractParentCommand parent = (AbstractParentCommand) parentSpec.userObject();
            for (AbstractCommand subcommand : parent.getSubcommands()) {
                final StabilityLevel annotation = subcommand.getClass().getAnnotation(StabilityLevel.class);
                final Stability level;
                if (annotation == null) {
                    level = Stability.Default;
                } else {
                    level = annotation.level();
                }
                if (DistributionInfo.getStability().permits(level)) {
                    // if it is Parent, get subcomands and push them into commands
                    final CommandLine.Model.CommandSpec spec = removeRestrictedArguments(CommandLine.Model.CommandSpec.forAnnotatedObject(subcommand));
                    parentSpec.addSubcommand(spec.name(), spec);
                    if (subcommand instanceof AbstractParentCommand) {
                        commands.push(spec);
                    }
                }
            }
        }

        return commandLine;
    }

    private CommandLine.Model.CommandSpec removeRestrictedArguments(CommandLine.Model.CommandSpec commandSpec) {
        ArrayList<CommandLine.Model.OptionSpec> toRemove = new ArrayList<>();
        for (CommandLine.Model.OptionSpec option : commandSpec.options()) {
            final Field field = (Field) option.userObject();
            final StabilityLevel annotation = field.getAnnotation(StabilityLevel.class);
            final Stability level;
            if (annotation == null) {
                level = Stability.Default;
            } else {
                level = annotation.level();
            }
            if (!DistributionInfo.getStability().permits(level)) {
                toRemove.add(option);
            }
        }

        for (CommandLine.Model.OptionSpec optionSpec : toRemove) {
            commandSpec = commandSpec.remove(optionSpec);
        }
        return commandSpec;
    }
}
