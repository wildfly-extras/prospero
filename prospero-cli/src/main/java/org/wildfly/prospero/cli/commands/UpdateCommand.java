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
import org.wildfly.prospero.cli.CliMain;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
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
    public static final String DIR_OR_SELF_IS_MANDATORY =
            String.format("Target dir argument (--%s) need to be set on update command", CliMain.TARGET_PATH_ARG);

    private final Logger logger = Logger.getLogger(this.getClass());

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    @CommandLine.Option(names = CliConstants.DRY_RUN)
    boolean dryRun;

    @CommandLine.Option(names = CliConstants.SELF)
    boolean self;

    @CommandLine.Option(names = CliConstants.LOCAL_REPO)
    Optional<Path> localRepo;

    @CommandLine.Option(names = CliConstants.OFFLINE)
    boolean offline;

    @CommandLine.Option(names = {CliConstants.Y, CliConstants.YES})
    boolean yes;

    public UpdateCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        if (self) {
            if (directory.isEmpty()) {
                directory = Optional.of(detectInstallationPath());
            }

            verifyInstallationContainsOnlyProspero(directory.get());
        }

        if (directory.isEmpty()) {
            console.error(DIR_OR_SELF_IS_MANDATORY, CliMain.TARGET_PATH_ARG);
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        if (offline && localRepo.isEmpty()) {
            console.error(CliMessages.MESSAGES.offlineModeRequiresLocalRepo());
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        try {
            final MavenSessionManager mavenSessionManager = new MavenSessionManager(localRepo, offline);

            final Path targetPath = directory.get().toAbsolutePath();
            UpdateAction updateAction = actionFactory.update(targetPath, mavenSessionManager, console);
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


    private void verifyInstallationContainsOnlyProspero(Path dir) throws ArgumentParsingException {
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

    private Path detectInstallationPath() throws ArgumentParsingException {
        final String modulePath = System.getProperty(JBOSS_MODULE_PATH);
        if (modulePath == null) {
            throw new ArgumentParsingException(CliMessages.MESSAGES.unableToLocateInstallation());
        }
        return Paths.get(modulePath).toAbsolutePath().getParent();
    }

}
