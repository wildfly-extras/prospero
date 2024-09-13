package org.wildfly.prospero.cli.commands.certificate;

import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.commands.AbstractParentCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name= CliConstants.Commands.CERTIFICATE)
public class CertificatesCommand extends AbstractParentCommand {
    public CertificatesCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory, CliConstants.Commands.CERTIFICATE, List.of(
                new CertificateAddCommand(console, actionFactory),
                new CertificateRemoveCommand(console, actionFactory),
                new CertificateListCommand(console, actionFactory))
        );
    }
}
