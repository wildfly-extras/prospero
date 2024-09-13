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

import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import org.wildfly.prospero.actions.CertificateAction;
import org.wildfly.prospero.signatures.PGPPublicKey;
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

@CommandLine.Command(name = CliConstants.Commands.ADD)
public class CertificateAddCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.DIR)
    private Optional<Path> installationDir;

    @CommandLine.Option(names = CliConstants.CERTIFICATE_FILE, required = true)
    private Path certificateFile;

    @CommandLine.Option(names = { CliConstants.Y, CliConstants.YES})
    private boolean forceAccept;

    public CertificateAddCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        long start = System.currentTimeMillis();
        final Path serverDir = determineInstallationDirectory(installationDir);

        try (CertificateAction certificateAction = actionFactory.certificateAction(serverDir)) {
            if (!Files.exists(certificateFile.toAbsolutePath()) || !Files.isReadable(certificateFile.toAbsolutePath())) {
                throw CliMessages.MESSAGES.certificateNonExistingFilePath(certificateFile.toAbsolutePath());
            }

            final PGPPublicKeyInfo keyInfo = PGPPublicKeyInfo.parse(certificateFile.toAbsolutePath().toFile());

            console.println(CliMessages.MESSAGES.certificateImportHeader());
            new KeyPrinter(console.getStdOut()).print(keyInfo);
            console.emptyLine();

            if (forceAccept || console.confirm(CliMessages.MESSAGES.certificateImportConfirmation(),
                    CliMessages.MESSAGES.certificateImportConfirmed(keyInfo.getKeyID().getHexKeyID()),
                    CliMessages.MESSAGES.certificateImportCancelled(keyInfo.getKeyID().getHexKeyID()))) {
                console.emptyLine();

                final PGPPublicKey trustCertificate = new PGPPublicKey(certificateFile.toAbsolutePath().toFile());
                certificateAction.importCertificate(trustCertificate);

                console.println(CliMessages.MESSAGES.operationCompleted((float) (System.currentTimeMillis() - start) /1000));
            }
        }

        return ReturnCodes.SUCCESS;
    }
}
