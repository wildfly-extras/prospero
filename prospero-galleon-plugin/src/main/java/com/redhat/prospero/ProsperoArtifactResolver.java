/*
 * Copyright 2016-2021 Red Hat, Inc. and/or its affiliates
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

package com.redhat.prospero;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Gav;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.xml.ManifestXmlSupport;
import com.redhat.prospero.xml.XmlException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;

public class ProsperoArtifactResolver {

    private final List<Channel> channels;
    private final Set<MavenArtifact> resolvedArtifacts = new HashSet<>();
    private MavenResolver repository;

    public ProsperoArtifactResolver(Path channelFile,
            RepositorySystem repoSystem, RepositorySystemSession repoSession,
            RepositorySystemSession fallbackRepoSession, List<RemoteRepository> fallbackRepositories) throws ProvisioningException {
        this(readChannels(channelFile), repoSystem, repoSession, fallbackRepoSession, fallbackRepositories);
    }

    private ProsperoArtifactResolver(List<Channel> channels,
            RepositorySystem repoSystem, RepositorySystemSession repoSession,
            RepositorySystemSession fallbackRepoSession, List<RemoteRepository> fallbackRepositories) throws ProvisioningException {
        this.channels = channels;
        repository = new MavenResolver(channels, repoSystem, repoSession, fallbackRepoSession, fallbackRepositories);
    }

    public List<RemoteRepository> getRepositories() {
        return repository.getRepositories();
    }

    private static List<Channel> readChannels(Path channelFile) throws ProvisioningException {
        try {
            return Channel.readChannels(channelFile);
        } catch (IOException e) {
            throw new ProvisioningException(e);
        }
    }

    public void writeManifestFile(Path home, Set<MavenArtifact> artifactSet) throws MavenUniverseException {
        List<com.redhat.prospero.api.Artifact> artifacts = new ArrayList<>();
        for (MavenArtifact artifact : artifactSet) {
            artifacts.add(new com.redhat.prospero.api.Artifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
                    artifact.getClassifier(), artifact.getExtension()));
        }

        try {
            ManifestXmlSupport.write(new Manifest(artifacts, Collections.emptyList(), home.resolve("manifest.xml")));
        } catch (XmlException e) {
            e.printStackTrace();
        }

        // write channels into installation
        final File channelsFile = home.resolve("channels.json").toFile();
        try {
            com.redhat.prospero.api.Channel.writeChannels(channels, channelsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void resolveLatest(MavenArtifact artifact) throws MavenUniverseException {
        if (artifact.isResolved()) {
            throw new MavenUniverseException("Artifact is already resolved");
        }
        String range;
        if (artifact.getVersionRange() == null) {
            if (artifact.getVersion() == null) {
                throw new MavenUniverseException("Can't compute range, version is not set for " + artifact);
            }
            range = "[" + artifact.getVersion() + ",)";
        } else {
            if (artifact.getVersion() != null) {
                System.out.println("WARNING: Version is set for " + artifact + " although a range is provided " + artifact.getVersionRange());
            }
            range = artifact.getVersionRange();
        }
        try {
            final com.redhat.prospero.api.Artifact prosperoArtifact = new com.redhat.prospero.api.Artifact(artifact.getGroupId(),
                    artifact.getArtifactId(), artifact.getVersion(), artifact.getClassifier(), artifact.getExtension());
            // First attempt to get the highest version from the channels
            Gav gav = repository.findLatestVersionOf(prosperoArtifact, range);
            if (gav == null) {
                System.out.println("The artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId() + " not found in channel, falling back");
                if (artifact.getVersion() == null) {
                    System.out.println("We must first retrieve the version");
                    gav = repository.findLatestFallBack(prosperoArtifact, range);
                } else {
                    System.out.println("Re-using artifact version " + artifact.getVersion());
                    gav = prosperoArtifact.newVersion(artifact.getVersion());
                }
                if (gav == null) {
                    throw new MavenUniverseException("Artifact is not found " + artifact.getGroupId() + ":" + artifact.getArtifactId());
                }
                final File resolvedPath = repository.resolveFallback(prosperoArtifact.newVersion(gav.getVersion()));
                artifact.setVersion(gav.getVersion());
                artifact.setPath(resolvedPath.toPath());
            } else {
                final File resolvedPath = repository.resolve(prosperoArtifact.newVersion(gav.getVersion()));
                artifact.setVersion(gav.getVersion());
                artifact.setPath(resolvedPath.toPath());
            }
            System.out.println("LATEST: version " + artifact.getVersion() + " for range " + range);
        } catch (ArtifactNotFoundException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        }
        if ("jar".equals(artifact.getExtension())) {
            resolvedArtifacts.add(artifact);
        }
    }

    VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
        VersionRangeResult res = repository.getVersionRange(artifact);
        if (res.getHighestVersion() == null) {
            res = repository.getVersionRangeFallback(artifact);
        }
        return res;
    }

    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        if (artifact.isResolved()) {
            throw new MavenUniverseException("Artifact is already resolved");
        }
        if (artifact.getVersion() == null) {
            throw new MavenUniverseException("Version is not set for " + artifact);
        }
        if (artifact.getVersionRange() != null) {
            System.out.println("WARNING: Version range is set for " + artifact);
        }

        final com.redhat.prospero.api.Artifact prosperoArtifact = new com.redhat.prospero.api.Artifact(artifact.getGroupId(),
                artifact.getArtifactId(), artifact.getVersion(), artifact.getClassifier(), artifact.getExtension());
        try {
            final File resolvedPath = repository.resolve(prosperoArtifact);
            artifact.setPath(resolvedPath.toPath());
        } catch (ArtifactNotFoundException ex) {
            System.out.println("FALLBACK: The artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId() + " not found in channel, falling back");
            try {
                final File resolvedPath = repository.resolveFallback(prosperoArtifact);
                artifact.setPath(resolvedPath.toPath());
            } catch (ArtifactNotFoundException e) {
                throw new MavenUniverseException(ex.getLocalizedMessage(), e);
            }
        }
        System.out.println("RESOLVED: " + artifact);
        if ("jar".equals(artifact.getExtension())) {
            resolvedArtifacts.add(artifact);
        }
    }

    void provisioningDone(Path home) throws MavenUniverseException {
        writeManifestFile(home, resolvedArtifacts);
    }

}
