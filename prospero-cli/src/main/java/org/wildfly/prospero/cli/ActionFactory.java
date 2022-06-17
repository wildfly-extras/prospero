package org.wildfly.prospero.cli;


import java.nio.file.Path;

import org.jboss.galleon.ProvisioningException;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistory;
import org.wildfly.prospero.actions.Provision;
import org.wildfly.prospero.actions.Update;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

public class ActionFactory {

    public Provision install(Path targetPath, MavenSessionManager mavenSessionManager, Console console) {
        return new Provision(targetPath, mavenSessionManager, console);
    }

    public Update update(Path targetPath, MavenSessionManager mavenSessionManager, Console console) throws OperationException,
            ProvisioningException {
        return new Update(targetPath, mavenSessionManager, console);
    }

    public InstallationHistory history(Path targetPath, Console console) {
        return new InstallationHistory(targetPath, console);
    }
}
