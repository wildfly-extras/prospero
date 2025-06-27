package org.wildfly.prospero.cli;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

import org.wildfly.prospero.DistributionInfo;
import org.wildfly.prospero.stability.Stability;
import org.wildfly.prospero.stability.StabilityLevel;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.AbstractParentCommand;
import picocli.CommandLine;

/**
 * A command builder that filters commands and options based on stability level annotations.
 * <p>
 * This builder creates {@link CommandLine} instances that respect stability level restrictions
 * by filtering out commands and options that are not permitted at the current distribution's
 * stability level. It processes {@link StabilityLevel} annotations on command classes and
 * option fields to determine what should be included in the final command structure.
 * </p>
 *
 * <h3>Filtering Behavior</h3>
 * <ul>
 * <li><strong>Commands</strong>: Subcommands annotated with {@code @StabilityLevel} are only
 *     included if the current stability permits their required level</li>
 * <li><strong>Options</strong>: Command-line options (fields) annotated with {@code @StabilityLevel}
 *     are removed from the command specification if not permitted</li>
 * <li><strong>Default Behavior</strong>: Commands and options without annotations are always included</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>
 * This class is thread-safe for concurrent building operations. Each {@link #build(BuildableCommand)}
 * call caches the current stability level to ensure consistent filtering throughout the build process,
 * even if the global stability level changes during building.
 * </p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * StabilityAwareCommandBuilder builder = new StabilityAwareCommandBuilder();
 * CommandLine commandLine = builder.build(mainCommand);
 *
 * // The resulting CommandLine will only contain commands and options
 * // that are permitted at the current distribution stability level
 * }</pre>
 *
 * @since 1.4.0
 * @see StabilityLevel
 * @see Stability
 * @see DistributionInfo#getStability()
 */
class StabilityAwareCommandBuilder {


    /**
     * Cached stability level for the current build operation.
     * <p>
     * This field caches the stability level at the start of each build operation
     * to ensure consistent filtering throughout the build process and to avoid
     * repeated calls to {@link DistributionInfo#getStability()}.
     * </p>
     */
    private volatile Stability currentStability;

    /**
     * Builds a {@link CommandLine} instance with stability-aware filtering.
     * <p>
     * This method creates a complete command structure by processing the main command
     * and all its subcommands, filtering out any commands or options that are not
     * permitted at the current distribution's stability level.
     * </p>
     *
     * @param mainCommand the root command to build from
     * @return a {@link CommandLine} instance with filtered commands and options
     * @throws NullPointerException if mainCommand is null
     * @throws IllegalStateException if a null subcommand is encountered during processing
     */
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

    /**
     * Recursively processes subcommands and filters them based on stability levels.
     * <p>
     * This method traverses the command hierarchy using a depth-first approach,
     * filtering subcommands based on their {@link StabilityLevel} annotations.
     * Only commands that are permitted at the current stability level are added
     * to the final command structure.
     * </p>
     *
     * @param mainSpec the root command specification to process
     * @throws IllegalStateException if a null subcommand is encountered
     */
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

    /**
     * Checks if a command is permitted at the current stability level.
     * <p>
     * This method examines the {@link StabilityLevel} annotation on the command class
     * to determine if it should be included in the command structure. Commands without
     * the annotation are considered to be at {@link Stability#Default} level.
     * </p>
     *
     * @param command the command to check
     * @return {@code true} if the command is permitted, {@code false} otherwise
     */
    private boolean isCommandPermitted(AbstractCommand command) {
        if (command == null) {
            return false;
        }

        final StabilityLevel annotation = command.getClass().getAnnotation(StabilityLevel.class);
        final Stability level = annotation != null ? annotation.level() : Stability.Default;
        return currentStability.permits(level);
    }

    /**
     * Removes command-line options that are not permitted at the current stability level.
     * <p>
     * This method creates a new command specification with options filtered based on
     * their {@link StabilityLevel} annotations. Options that are not permitted are
     * removed from the specification, making them unavailable in the CLI.
     * </p>
     *
     * @param commandSpec the original command specification
     * @return a new command specification with restricted options removed
     * @throws NullPointerException if commandSpec is null
     */
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

    /**
     * Checks if a command-line option is permitted at the current stability level.
     * <p>
     * This method examines the {@link StabilityLevel} annotation on the option field
     * to determine if it should be included in the command specification. Options without
     * the annotation are considered to be at {@link Stability#Default} level and are
     * always included.
     * </p>
     *
     * @param option the option specification to check
     * @return {@code true} if the option is permitted, {@code false} otherwise
     */
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
