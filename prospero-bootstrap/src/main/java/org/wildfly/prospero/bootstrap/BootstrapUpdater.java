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

package org.wildfly.prospero.bootstrap;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.Stream;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;

public class BootstrapUpdater {

    static String LOCAL_MAVEN_REPO = System.getProperty("user.home") + "/.m2/repository";

    public List<Path> update(String[] args) throws BootstrapException {
        final Path userHome = Paths.get(System.getProperty("user.home"));
        final Path installerLib = userHome.resolve(".jboss-installer").resolve("lib");

        Optional<String> channelRepo = Optional.empty();
        for (String arg : args) {
            if (!arg.startsWith("--channel-repo=")) {
                continue;
            }
            channelRepo = Optional.of(arg.substring("--channel-repo=".length()));
        }

        return downloadAllDeps(installerLib, channelRepo);
    }

    private List<Path> downloadAllDeps(Path installerLib, Optional<String> channelRepo) throws BootstrapException {
        try {
            final RemoteRepository repo = new RemoteRepository.Builder("mrrc", "default", channelRepo.orElse("https://maven.repository.redhat.com/ga/")).build();
            final RepositorySystem system = newRepositorySystem();
            final DefaultRepositorySystemSession repoSession = newRepositorySystemSession(system, true);
            final MavenVersionsResolver.Factory factory = new VersionResolverFactory(system, repoSession, Arrays.asList(repo));

            final MavenVersionsResolver mavenResolver = factory.create();
            final Set<String> allVersions = mavenResolver.getAllVersions("org.wildfly.channels", "installer", "yaml", "channel");
            if (allVersions.isEmpty()) {
                throw new BootstrapException("Unable to find installer channel definition");
            }
            final String latestVersion = allVersions.stream().sorted(VersionMatcher.COMPARATOR.reversed()).findFirst().get();
            URL url = mavenResolver
                    .resolveArtifact("org.wildfly.channels", "installer", "yaml", "channel", latestVersion)
                    .toURI().toURL();

            final Channel channel = ChannelMapper.from(url);

            final ChannelSession session = new ChannelSession(Arrays.asList(channel), factory);
            final ChannelSession channelSession = session;

            List<Path> previousVersions = new ArrayList<>();
            for (Stream stream : channel.getStreams()) {
                final String groupId = stream.getGroupId();
                final String artifactId = stream.getArtifactId();
                final String extension = "jar";
                final MavenArtifact artifact = channelSession.resolveMavenArtifact(groupId, artifactId, extension, null, null);
                final Path targetPath = installerLib.resolve(artifact.getFile().getName());
                if (!targetPath.toFile().exists()) {
                    Files.copy(artifact.getFile().toPath(), targetPath);
                    // find if there is previous version
                    Optional<Path> prev = findPreviousVersion(artifact, installerLib);
                    prev.ifPresent(previousVersions::add);
                }
            }

            return previousVersions;
        } catch (UnresolvedMavenArtifactException | IOException e) {
            throw new BootstrapException(e);
        }
    }

    private RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    private DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, boolean resolveLocalCache) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        String location;
        if (resolveLocalCache) {
            location = LOCAL_MAVEN_REPO;
        } else {
            location = "target/local-repo";
        }
        LocalRepository localRepo = new LocalRepository(location);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    private Optional<Path> findPreviousVersion(MavenArtifact artifact, Path installerLib) {
        for (String fileName : installerLib.toFile().list()) {
            // TODO: handle classifier
            if (fileName.startsWith(artifact.getArtifactId()) && fileName.endsWith(artifact.getExtension())
                    && !fileName.equals(artifact.getFile().getName())) {
                return Optional.of(installerLib.resolve(fileName));
            }
        }
        return Optional.empty();
    }

}
