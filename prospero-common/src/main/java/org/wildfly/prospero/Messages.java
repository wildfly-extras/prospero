/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero;

import java.net.URL;
import java.nio.file.Path;

import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.NoChannelException;

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
    NoChannelException noChannelReference();

    @Message("[%s] doesn't specify any channels and no additional channels are selected.")
    NoChannelException fplDefinitionDoesntContainChannel(String fpl);

    @Message("Repository with ID '%s' is not present.")
    IllegalArgumentException repositoryNotPresent(String repoId);

    @Message("Channel '%s' is not present.")
    IllegalArgumentException channelNotPresent(String urlOrGav);

    @Message("Repository '%s' with URL '%s' is alreay present.")
    IllegalArgumentException repositoryExists(String repoId, URL url);

    @Message("Channel '%s' is alreay present.")
    IllegalArgumentException channelExists(String urlOrGav);

    @Message("Installing patch %s.")
    String installingPatch(String name);

    @Message("Provided FPL has invalid format `%s`.")
    String invalidFpl(String fplText);
}
