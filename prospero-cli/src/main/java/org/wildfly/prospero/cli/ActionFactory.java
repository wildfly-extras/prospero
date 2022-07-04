package org.wildfly.prospero.cli;


import java.nio.file.Path;

import org.jboss.galleon.ProvisioningException;
import org.wildfly.prospero.actions.ApplyPatchAction;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

public class ActionFactory {

    public ProvisioningAction install(Path targetPath, MavenSessionManager mavenSessionManager, Console console) {
        return new ProvisioningAction(targetPath, mavenSessionManager, console);
    }

    public UpdateAction update(Path targetPath, MavenSessionManager mavenSessionManager, Console console) throws OperationException,
            ProvisioningException {
        return new UpdateAction(targetPath, mavenSessionManager, console);
    }

    public InstallationHistoryAction history(Path targetPath, Console console) {
        return new InstallationHistoryAction(targetPath, console);
    }

    public MetadataAction metadataActions(Path targetPath) {
        return new MetadataAction(targetPath);
    }

    public ApplyPatchAction applyPatch(Path targetPath, MavenSessionManager mavenSessionManager, Console console) throws OperationException {
        return new ApplyPatchAction(targetPath, mavenSessionManager, console);
    }
}
