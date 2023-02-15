/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.actions;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.maven.ChannelCoordinate;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.exceptions.ArtifactPromoteException;
import org.wildfly.prospero.promotion.ArtifactPromoter;
import org.wildfly.prospero.promotion.ArtifactBundle;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;

public class PromoteArtifactBundleAction {

    private final Console console;

    public PromoteArtifactBundleAction(Console console) {
        this.console = console;
    }

    public void promote(Path archive, URL targetRepository, ChannelManifestCoordinate coordinate) throws ProvisioningException, ArtifactPromoteException {
        Objects.requireNonNull(archive);
        Objects.requireNonNull(targetRepository);
        Objects.requireNonNull(coordinate);

        if (coordinate.getMaven() == null) {
            throw Messages.MESSAGES.nonMavenChannelRef();
        }

        try (final ArtifactBundle extracted = ArtifactBundle.extract(archive)) {
            console.println(Messages.MESSAGES.promotingArtifacts(targetRepository));
            for (ArtifactCoordinate artifact : extracted.getArtifactList()) {
                console.println("  * " + String.format("%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
            }
            final MavenSessionManager msm = new MavenSessionManager(MavenOptions.OFFLINE_NO_CACHE);
            final RepositorySystem system = msm.newRepositorySystem();
            final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system);

            RemoteRepository sourceRepo = new RemoteRepository.Builder("source-repo", "default", extracted.getRepository().toUri().toURL().toString()).build();
            RemoteRepository targetRepo = new RemoteRepository.Builder("target-repo", "default", targetRepository.toString()).build();

            final ArtifactPromoter promoter = new ArtifactPromoter(system, session, targetRepo);
            try {
                promoter.promote(extracted.getArtifactList(),
                        new ChannelCoordinate(coordinate.getMaven().getGroupId(), coordinate.getMaven().getArtifactId()), sourceRepo);
            } catch (IOException | ArtifactResolutionException | DeploymentException e) {
                throw Messages.MESSAGES.unableToPromote(targetRepository, e);
            }
        } catch (IOException e) {
            throw Messages.MESSAGES.unableToParseCustomizationBundle(archive, e);
        }
    }
}
