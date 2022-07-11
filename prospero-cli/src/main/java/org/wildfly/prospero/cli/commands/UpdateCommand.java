package org.wildfly.prospero.cli.commands;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.Logger;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ArgumentParsingException;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.options.LocalRepoOptions;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.UPDATE,
        sortOptions = false
)
public class UpdateCommand extends AbstractCommand {

    public static final String JBOSS_MODULE_PATH = "module.path";
    public static final String PROSPERO_FP_GA = "org.wildfly.prospero:prospero-standalone-galleon-pack";
    public static final String PROSPERO_FP_ZIP = PROSPERO_FP_GA + "::zip";

    private final Logger logger = Logger.getLogger(this.getClass());

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    @CommandLine.Option(names = CliConstants.DRY_RUN)
    boolean dryRun;

    @CommandLine.Option(names = CliConstants.SELF)
    boolean self;

    @CommandLine.ArgGroup(exclusive = true)
    LocalRepoOptions localRepoOptions;

    @CommandLine.Option(names = CliConstants.OFFLINE)
    boolean offline;

    @CommandLine.Option(names = {CliConstants.Y, CliConstants.YES})
    boolean yes;

    public UpdateCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        final Path installationDir;

        if (self) {
            if (directory.isPresent()) {
                installationDir = directory.get().toAbsolutePath();
            } else {
                installationDir = detectProsperoInstallationPath().toAbsolutePath();
            }
            verifyInstallationContainsOnlyProspero(installationDir);
        } else {
            installationDir = determineInstallationDirectory(directory);
        }

        try {
            final MavenSessionManager mavenSessionManager = new MavenSessionManager(LocalRepoOptions.getLocalRepo(localRepoOptions), offline);

            UpdateAction updateAction = actionFactory.update(installationDir, mavenSessionManager, console);
            if (!dryRun) {
                updateAction.doUpdateAll(yes);
            } else {
                updateAction.listUpdates();
            }
        } catch (MetadataException | ProvisioningException e) {
            console.error(CliMessages.MESSAGES.errorWhileExecutingOperation(CliConstants.UPDATE, e.getMessage()));
            logger.error(CliMessages.MESSAGES.errorWhileExecutingOperation(CliConstants.INSTALL, e.getMessage()), e);
            return ReturnCodes.PROCESSING_ERROR;
        }
        return ReturnCodes.SUCCESS;
    }


    private static void verifyInstallationContainsOnlyProspero(Path dir) throws ArgumentParsingException {
        verifyInstallationDirectory(dir);

        try {
            final List<String> fpNames = GalleonUtils.getInstalledPacks(dir.toAbsolutePath());
            if (fpNames.size() != 1) {
                throw new ArgumentParsingException(CliMessages.MESSAGES.unexpectedPackageInSelfUpdate(dir.toString()));
            }
            if (!fpNames.stream().allMatch(PROSPERO_FP_ZIP::equals)) {
                throw new ArgumentParsingException(CliMessages.MESSAGES.unexpectedPackageInSelfUpdate(dir.toString()));
            }
        } catch (ProvisioningException e) {
            throw new ArgumentParsingException(CliMessages.MESSAGES.unableToParseSelfUpdateData(), e);
        }
    }

    private static Path detectProsperoInstallationPath() throws ArgumentParsingException {
        final String modulePath = System.getProperty(JBOSS_MODULE_PATH);
        if (modulePath == null) {
            throw new ArgumentParsingException(CliMessages.MESSAGES.unableToLocateProsperoInstallation());
        }
        return Paths.get(modulePath).toAbsolutePath().getParent();
    }

}
