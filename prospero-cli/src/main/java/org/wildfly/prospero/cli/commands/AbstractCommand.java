/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.jboss.logging.Logger;
import org.wildfly.prospero.stability.Stability;
import org.wildfly.prospero.stability.StabilityLevel;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ArgumentParsingException;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import picocli.CommandLine;

public abstract class AbstractCommand implements Callable<Integer> {

    protected final CliConsole console;
    protected final ActionFactory actionFactory;

    private static final Logger log = Logger.getLogger(AbstractCommand.class);

    @SuppressWarnings("unused")
    @CommandLine.Option(
            names = {CliConstants.H, CliConstants.HELP},
            usageHelp = true,
            order = 100
    )
    boolean help;

    @CommandLine.Option(
            names = {CliConstants.VV, CliConstants.VERBOSE},
            order = 101
    )
    boolean verbose;

    @SuppressWarnings("unused")
    @CommandLine.Option(
            names = {CliConstants.DEBUG},
            order = 102
    )
    boolean debug;

    @CommandLine.Option(names = CliConstants.STABILITY, converter = StabilityConverter.class )
    @StabilityLevel(level = Stability.Community)
    Stability stability;

    private static class StabilityConverter implements CommandLine.ITypeConverter<Stability> {

        @Override
        public Stability convert(String value) throws Exception {
            return Stability.from(value);
        }
    }

    public AbstractCommand(CliConsole console, ActionFactory actionFactory) {
        this.console = console;
        this.actionFactory = actionFactory;
    }

    protected static Path determineInstallationDirectory(Optional<Path> directoryOption) throws ArgumentParsingException {
        return determineInstallationDirectory(directoryOption, currentDir());
    }

    static Path determineInstallationDirectory(Optional<Path> directoryOption, Path currentDir) throws ArgumentParsingException {
        Path installationDirectory = directoryOption.orElse(currentDir).toAbsolutePath();

        // Check if the directory option was provided
        if (directoryOption.isPresent()) {
            // If --dir is present, verify the provided directory
            if (!verifyDirectoryContainsInstallation(installationDirectory)) {
                throw CliMessages.MESSAGES.invalidInstallationDir(installationDirectory);
            }
        } else {
            // If --dir is not present, iterate through parent directories until a valid installation directory is found
            while (!verifyDirectoryContainsInstallation(installationDirectory)) {
                installationDirectory = installationDirectory.getParent();
                if (installationDirectory == null) {
                    throw CliMessages.MESSAGES.invalidInstallationDirMaybeUseDirOption(currentDir().toAbsolutePath());
                }
            }
        }

        return installationDirectory;
    }

    static boolean verifyDirectoryContainsInstallation(Path path) {
        Path dotGalleonDir = path.resolve(InstallationMetadata.GALLEON_INSTALLATION_DIR);
        Path channelsFile = path.resolve(ProsperoMetadataUtils.METADATA_DIR)
                .resolve(ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME);

        return Files.isDirectory(dotGalleonDir) && Files.isRegularFile(channelsFile);
    }

    static Path currentDir() {
        return Paths.get(".").toAbsolutePath();
    }

    protected static void verifyTargetDirectoryIsEmpty(Path path) {
        if (Files.exists(path)) {
            if (!Files.isDirectory(path)) {
                log.debug("Target is not a directory");
                throw CliMessages.MESSAGES.nonEmptyTargetFolder(path);
            }
            if (path.toFile().list().length != 0) {
                log.debug("Target folder is not empty");
                throw CliMessages.MESSAGES.nonEmptyTargetFolder(path);
            }
        }
        if (!isWritable(path)) {
            log.debug("Target is not writable");
            throw CliMessages.MESSAGES.nonEmptyTargetFolder(path);
        }
    }

    private static boolean isWritable(final Path path) {
        Path absPath = path.toAbsolutePath();
        if (Files.exists(absPath)) {
            return Files.isWritable(absPath);
        } else {
            if (absPath.getParent() == null) {
                return false;
            } else {
                return isWritable(absPath.getParent());
            }
        }
    }
}
