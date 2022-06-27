package org.wildfly.prospero;

import java.nio.file.Path;

import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

@MessageBundle(projectCode = "PRSP")
public interface Messages {

    Messages MESSAGES = org.jboss.logging.Messages.getBundle(Messages.class);

    @Message("Given path %s is a regular file. An empty directory or a non-existing path must be given.")
    IllegalArgumentException dirMustBeDirectory(Path path);

    @Message("Can't install into a non empty directory %s. Use `update` command if you want to modify existing installation.")
    IllegalArgumentException cannotInstallIntoNonEmptyDirectory(Path path);
}
