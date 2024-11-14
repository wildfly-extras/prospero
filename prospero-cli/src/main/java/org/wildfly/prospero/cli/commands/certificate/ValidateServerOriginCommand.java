/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.prospero.cli.commands.certificate;

import java.nio.file.Path;

import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.prospero.actions.CertificateAction;
import org.wildfly.prospero.actions.VerificationResult;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractMavenCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import picocli.CommandLine;

@CommandLine.Command(name = CliConstants.Commands.VALIDATE_SERVER)
public class ValidateServerOriginCommand extends AbstractMavenCommand {

    public ValidateServerOriginCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        final long startTime = System.currentTimeMillis();
        final Path serverDir = determineInstallationDirectory(directory);

        try (CertificateAction certificateAction = actionFactory.certificateAction(serverDir);
             final VerificationConsole verificationConsole = new VerificationConsole(console)) {
            final VerificationResult verificationResult = certificateAction.verifyServerOrigin(verificationConsole, parseMavenOptions());

            console.println("");
            if (!verificationResult.getTrustedCertificates().isEmpty()) {
                console.println(CliMessages.MESSAGES.trustedCertificatesListHeader());
                for (PGPPublicKeyInfo trustedCertificate : verificationResult.getTrustedCertificates()) {
                    console.printf("  * [%s] %s%n",
                            trustedCertificate.getKeyID().getHexKeyID(),
                            String.join(";", trustedCertificate.getIdentity())
                    );
                }
                console.println("");
            }

            if (verificationResult.getUnsignedBinary().isEmpty()) {
                console.println(CliMessages.MESSAGES.verifiedComponentsOnly());
            } else {
                console.println(CliMessages.MESSAGES.unverifiedComponentsListHeader());
                for (VerificationResult.InvalidBinary invalidBinary : verificationResult.getUnsignedBinary()) {
                    console.printf("  * %s : %s%n",
                            invalidBinary.getPath().toString(),
                            getErrorDescription(invalidBinary.getError(), invalidBinary.getKeyId())
                    );
                }
                console.println("");
            }

            if (!verificationResult.getModifiedFiles().isEmpty()) {
                console.println(CliMessages.MESSAGES.modifiedFilesListHeader());
                for (Path modifiedFile : verificationResult.getModifiedFiles()) {
                    console.printf("  * %s%n", modifiedFile.toString());
                }
            }
            final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
            console.println("");
            console.println(CliMessages.MESSAGES.operationCompleted(totalTime));
            if (verificationResult.getUnsignedBinary().isEmpty()) {
                return ReturnCodes.SUCCESS;
            } else {
                return ReturnCodes.PROCESSING_ERROR;
            }
        }
    }

    private static String getErrorDescription(SignatureResult.Result error, String keyId) {
        switch (error) {
            case NO_SIGNATURE:
                return CliMessages.MESSAGES.componentSignatureNotFound();
            case NO_MATCHING_CERT:
                return CliMessages.MESSAGES.componentPublicKeyNotFound(keyId);
            case INVALID:
                return CliMessages.MESSAGES.componentInvalidLocalFile();
            default:
                return CliMessages.MESSAGES.componentUnknownError(error.toString());
        }
    }
}
