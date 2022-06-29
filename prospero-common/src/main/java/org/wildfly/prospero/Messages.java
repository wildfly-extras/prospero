package org.wildfly.prospero;

import java.nio.file.Path;

import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.MetadataException;

@MessageBundle(projectCode = "PRSP")
public interface Messages {

    public Messages MESSAGES = org.jboss.logging.Messages.getBundle(Messages.class);

    @Message("Given path '%s' is a regular file. An empty directory or a non-existing path must be given.")
    IllegalArgumentException dirMustBeDirectory(Path path);

    @Message("Can't install into a non empty directory '%s'. Use `update` command if you want to modify existing installation.")
    IllegalArgumentException cannotInstallIntoNonEmptyDirectory(Path path);

    @Message("Installation dir '%s' doesn't exist")
    IllegalArgumentException installationDirDoesNotExist(Path path);

    @Message("Unable to resolve channel configuration")
    MetadataException unableToResolveChannelConfiguration(@Cause Exception exception);

    @Message("Installation dir '%s' already exists")
    ProvisioningException installationDirAlreadyExists(Path installDir);

    @Message("Installing %s")
    String installingFpl(String fpl);

    @Message("Artifact [%s:%s] not found")
    ArtifactResolutionException artifactNotFound(String g, String a, @Cause Exception e);

    @Message("At least one channel reference must be given.")
    IllegalArgumentException noChannelReference();
}
