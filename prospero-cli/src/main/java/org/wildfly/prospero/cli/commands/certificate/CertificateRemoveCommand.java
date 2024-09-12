package org.wildfly.prospero.cli.commands.certificate;

import org.wildfly.prospero.signatures.PGPKeyId;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import org.wildfly.prospero.actions.CertificateAction;
import org.wildfly.prospero.signatures.PGPRevokeSignature;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@CommandLine.Command(name = CliConstants.Commands.REMOVE)
public class CertificateRemoveCommand  extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.DIR)
    private Optional<Path> installationDir;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    private CertificateOptions certificateOptions;

    @CommandLine.Option(names = { CliConstants.Y, CliConstants.YES})
    private boolean forceAccept;

    static class CertificateOptions {
        @CommandLine.Option(names = CliConstants.KEY_ID)
        private String certificateName;

        @CommandLine.Option(names = CliConstants.REVOKE_CERTIFICATE)
        private Path revokeCertificatePath;
    }

    public CertificateRemoveCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        final Path serverDir = determineInstallationDirectory(installationDir);
        try (CertificateAction certificateAction = actionFactory.certificateAction(serverDir)) {
            if (certificateOptions.certificateName != null) {
                PGPPublicKeyInfo keyInfo = certificateAction.getCertificate(new PGPKeyId(certificateOptions.certificateName));
                if (keyInfo == null) {
                    console.error(CliMessages.MESSAGES.noSuchCertificate(certificateOptions.certificateName));
                    return ReturnCodes.INVALID_ARGUMENTS;
                }
                console.println(CliMessages.MESSAGES.certificateRemoveHeader(certificateOptions.certificateName));
                console.emptyLine();
                new KeyPrinter(console.getStdOut()).print(keyInfo);
                if (forceAccept || console.confirm(CliMessages.MESSAGES.certificateRemovePrompt(), "",
                        CliMessages.MESSAGES.certificateRemoveAbort())) {
                    certificateAction.removeCertificate(new PGPKeyId(certificateOptions.certificateName));
                    console.println(CliMessages.MESSAGES.certificateRemoved(certificateOptions.certificateName));
                }
            } else {
                if (!Files.exists(certificateOptions.revokeCertificatePath)) {
                    throw CliMessages.MESSAGES.nonExistingFilePath(certificateOptions.revokeCertificatePath);
                }
                final PGPRevokeSignature revokeCertificate = new PGPRevokeSignature(certificateOptions.revokeCertificatePath.toFile());
                final PGPPublicKeyInfo revokedCertificate = certificateAction.getCertificate(revokeCertificate.getRevokedKeyId());
                if (revokedCertificate == null) {
                    console.error(CliMessages.MESSAGES.noSuchCertificate(revokeCertificate.getRevokedKeyId().getHexKeyID()));
                    return ReturnCodes.INVALID_ARGUMENTS;
                }
                console.println(CliMessages.MESSAGES.certificateRevokeHeader(revokedCertificate.getKeyID().getHexKeyID()));
                console.emptyLine();
                new KeyPrinter(console.getStdOut()).print(revokedCertificate);
                if (forceAccept || console.confirm(CliMessages.MESSAGES.certificateRevokePrompt(), "",
                        CliMessages.MESSAGES.certificateRemoveAbort())) {
                    certificateAction.revokeCertificate(revokeCertificate);
                    console.println(CliMessages.MESSAGES.certificateRevoked());
                }
            }
        }

        return ReturnCodes.SUCCESS;
    }
}
