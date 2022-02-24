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

package com.redhat.prospero.wfchannel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.redhat.prospero.api.ProvisioningRuntimeException;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jboss.galleon.ProvisioningException;

public class MavenSessionManager {
    private static String LOCAL_MAVEN_REPO = System.getProperty("user.home") + "/.m2/repository";
    private final Path provisioningRepo;
    private boolean offline;

    public MavenSessionManager(Path provisioningRepo) {
        this.provisioningRepo = provisioningRepo;
    }

    public MavenSessionManager() throws ProvisioningException {
        try {
            provisioningRepo = Files.createTempDirectory("provisioning-repo");
            provisioningRepo.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new ProvisioningException("Unable to create provisioning repository folder.", e);
        }
    }

    public RepositorySystem newRepositorySystem() {
        final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                throw new ProvisioningRuntimeException("Failed to initiate maven repository system");
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    public DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system,
                                                                     boolean resolveLocalCache) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        String location;
        if (resolveLocalCache) {
            location = LOCAL_MAVEN_REPO;
        } else {
            location = provisioningRepo.toAbsolutePath().toString();
        }
        LocalRepository localRepo = new LocalRepository(location);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        session.setOffline(offline);
        return session;
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
