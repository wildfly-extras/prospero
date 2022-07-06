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

package org.wildfly.prospero.wfchannel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.repository.LocalRepository;
import org.jboss.logging.Logger;
import org.wildfly.prospero.api.exceptions.ProvisioningRuntimeException;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jboss.galleon.ProvisioningException;

public class MavenSessionManager {

    private static final Logger logger = Logger.getLogger(MavenSessionManager.class);

    public static final Path LOCAL_MAVEN_REPO = Paths.get(System.getProperty("user.home"), ".m2", "repository");
    private static final String AETHER_OFFLINE_PROTOCOLS_PROPERTY = "aether.offline.protocols";
    private static final String AETHER_OFFLINE_PROTOCOLS_VALUE = "file";
    private final Path provisioningRepo;
    private boolean offline;

    public MavenSessionManager(Optional<Path> provisioningRepo, boolean offline) throws ProvisioningException {
        this.offline = offline;
        if (provisioningRepo.isPresent()) {
            this.provisioningRepo = provisioningRepo.get().toAbsolutePath();
        } else {
            try {
                this.provisioningRepo = Files.createTempDirectory("provisioning-repo");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtils.deleteQuietly(this.provisioningRepo.toFile())));
            } catch (IOException e) {
                throw new ProvisioningException("Unable to create provisioning repository folder.", e);
            }
        }

        // allow to resolve file-system repositories in offline mode
        if (System.getProperty(AETHER_OFFLINE_PROTOCOLS_PROPERTY) == null) {
            System.setProperty(AETHER_OFFLINE_PROTOCOLS_PROPERTY, AETHER_OFFLINE_PROTOCOLS_VALUE);
            Runtime.getRuntime().addShutdownHook(new Thread(()-> System.clearProperty(AETHER_OFFLINE_PROTOCOLS_PROPERTY)));
        }
    }

    public MavenSessionManager(Path provisioningRepo) {
        this.provisioningRepo = provisioningRepo.toAbsolutePath();
    }

    public MavenSessionManager() throws ProvisioningException {
        this(Optional.empty(), false);
    }

    public RepositorySystem newRepositorySystem() {
        final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                throw new ProvisioningRuntimeException("Failed to initiate maven repository system");
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    public DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        return newRepositorySystemSession(system, false);
    }

    public DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system,
                                                                     boolean resolveLocalCache) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        Path location;
        if (resolveLocalCache) {
            location = LOCAL_MAVEN_REPO;
        } else {
            location = provisioningRepo.toAbsolutePath();
        }
        LocalRepository localRepo = new LocalRepository(location.toFile());
        if (resolveLocalCache) {
            copyResolvedArtifactsToProvisiongRepository(session);
        }
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        session.setOffline(offline);
        return session;
    }

    private void copyResolvedArtifactsToProvisiongRepository(DefaultRepositorySystemSession session) {
        // hack to work around Galleon provisioning again to generate examples
        // whenever an artifact is resolved by a repository using LOCAL_MAVE_REPO,
        // install the artifact into a temporary provisioningRepo. The provisioningRepo then is used
        // by Galleon to start thin server.
        final RepositorySystem localCacheBuilder = newRepositorySystem();
        final DefaultRepositorySystemSession localCacheBuilderSession = newRepositorySystemSession(localCacheBuilder, false);
        session.setRepositoryListener(new AbstractRepositoryListener() {
            @Override
            public void artifactResolved(RepositoryEvent event) {
                final InstallRequest request = new InstallRequest();
                request.addArtifact(event.getArtifact());
                try {
                    localCacheBuilder.install(localCacheBuilderSession, request);
                } catch (InstallationException e) {
                    // log and ignore
                    logger.warn("Unable to install resolved artifact in the provisioning repository", e);
                }
            }
        });
    }

    public Path getProvisioningRepo() {
        return provisioningRepo;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public boolean isOffline() {
        return offline;
    }
}
