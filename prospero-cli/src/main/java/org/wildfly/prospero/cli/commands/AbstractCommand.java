package org.wildfly.prospero.cli.commands;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import picocli.CommandLine;

public abstract class AbstractCommand implements Callable<Integer> {

    protected final Console console;
    protected final ActionFactory actionFactory;

    @SuppressWarnings("unused")
    @CommandLine.Option(
            names = {CliConstants.H, CliConstants.HELP},
            usageHelp = true,
            order = 100
    )
    boolean help;

    public AbstractCommand(Console console, ActionFactory actionFactory) {
        this.console = console;
        this.actionFactory = actionFactory;
    }

    static Path determineInstallationDirectory(Optional<Path> directoryOption) {
        Path installationDirectory = directoryOption.orElse(currentDir()).toAbsolutePath();
        verifyInstallationDirectory(installationDirectory);
        return installationDirectory;
    }

    static void verifyInstallationDirectory(Path path) {
        File dotGalleonDir = path.resolve(InstallationMetadata.GALLEON_INSTALLATION_DIR).toFile();
        File prosperoConfigFile = path.resolve(InstallationMetadata.METADATA_DIR)
                .resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME).toFile();
        if (!dotGalleonDir.isDirectory() || !prosperoConfigFile.isFile()) {
            throw CliMessages.MESSAGES.invalidInstallationDir(path);
        }
    }

    static Path currentDir() {
        return Paths.get(".").toAbsolutePath();
    }

}
