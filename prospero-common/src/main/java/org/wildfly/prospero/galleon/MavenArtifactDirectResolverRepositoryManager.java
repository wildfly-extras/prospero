/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.galleon;

import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.ChannelResolvable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A MavenRepoManager implementation that will do direct artifact resolving.
 * This works only on MavenArtifact resolving whose version is known.
 */
public class MavenArtifactDirectResolverRepositoryManager implements MavenRepoManager, ChannelResolvable {

    private final ChannelSession channelSession;

    public MavenArtifactDirectResolverRepositoryManager(ChannelSession channelSession) {
        this.channelSession = channelSession;
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        checkArtifact(artifact);
        org.wildfly.channel.MavenArtifact result;
        try {
            result = channelSession.resolveDirectMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(),
              artifact.getExtension(), artifact.getClassifier(), artifact.getVersion());
        } catch (UnresolvedMavenArtifactException ex) {
            // if the artifact can not be resolved directly either, we abort
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        }
        MavenArtifactMapper.resolve(artifact, result);
    }

    private void checkArtifact(MavenArtifact artifact) {
        if (artifact.getVersion() == null) {
            throw new IllegalArgumentException("Version of " + artifact + " is null");
        }
    }

    @Override
    public void resolveAll(Collection<MavenArtifact> artifacts) throws MavenUniverseException {
        artifacts.forEach(this::checkArtifact);
        MavenArtifactMapper mapper = new MavenArtifactMapper(artifacts);
        List<org.wildfly.channel.MavenArtifact> channelArtifacts = channelSession.resolveDirectMavenArtifacts(mapper.toChannelArtifacts());
        mapper.applyResolution(channelArtifacts);
    }

    @Override
    public boolean isResolved(MavenArtifact artifact) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean isLatestVersionResolved(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, boolean locallyAvailable) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void install(MavenArtifact artifact, Path path) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
