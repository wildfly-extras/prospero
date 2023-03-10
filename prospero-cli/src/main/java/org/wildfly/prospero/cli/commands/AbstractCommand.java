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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.jboss.logging.Logger;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ArgumentParsingException;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;
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

    public AbstractCommand(CliConsole console, ActionFactory actionFactory) {
        this.console = console;
        this.actionFactory = actionFactory;
    }

    protected static Path determineInstallationDirectory(Optional<Path> directoryOption) throws ArgumentParsingException {
        Path installationDirectory = directoryOption.orElse(currentDir()).toAbsolutePath();
        verifyDirectoryContainsInstallation(installationDirectory);
        return installationDirectory;
    }

    static void verifyDirectoryContainsInstallation(Path path) throws ArgumentParsingException {
        File dotGalleonDir = path.resolve(InstallationMetadata.GALLEON_INSTALLATION_DIR).toFile();
        File channelsFile = path.resolve(InstallationMetadata.METADATA_DIR)
                .resolve(InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME).toFile();
        if (!dotGalleonDir.isDirectory() || !channelsFile.isFile()) {
            if (currentDir().equals(path)) {
                // if the path is the current directory, user may have forgotten to specify the --dir option
                //   -> suggest to use --dir
                throw CliMessages.MESSAGES.invalidInstallationDirMaybeUseDirOption(path);
            } else {
                throw CliMessages.MESSAGES.invalidInstallationDir(path);
            }
        }
    }

    static Path currentDir() {
        return Paths.get(".").toAbsolutePath();
    }

    protected static void verifyTargetDirectoryIsEmpty(Path path) {
        if (Files.exists(path)) {
            if (!Files.isDirectory(path)) {
                log.debug("Target is not a directory");
                throw CliMessages.MESSAGES.nonEmptyTargetFolder();
            }
            if (path.toFile().list().length != 0) {
                log.debug("Target folder is not empty");
                throw CliMessages.MESSAGES.nonEmptyTargetFolder();
            }
        }
        if (!isWritable(path)) {
            log.debug("Target is not writable");
            throw CliMessages.MESSAGES.nonEmptyTargetFolder();
        }
    }

    private static boolean isWritable(final Path path) {
        Path absPath = path.toAbsolutePath();
        if (Files.exists(absPath)) {
            return Files.isWritable(absPath);
        } else {
            return isWritable(absPath.getParent());
        }
    }
}
