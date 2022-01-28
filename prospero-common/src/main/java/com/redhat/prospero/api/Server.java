/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.api;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.wildfly.channel.MavenRepository;

public enum Server {
   EAP, WILFDFLY;

   public String getFpl() {
      if (this == EAP) {
         return "org.jboss.eap:wildfly-ee-galleon-pack";
      } else {
         return "org.wildfly:wildfly-ee-galleon-pack";
      }
   }

   public List<ChannelRef> getChannelRefs() throws VersionRangeResolutionException, MalformedURLException, ArtifactResolutionException {
      final DefaultArtifact artifact;
      final RemoteRepository repo;
      final String repoUrl;
      if (this == EAP) {
         artifact = new DefaultArtifact("org.wildfly.channels", "eap-74", "channel", "yaml", "[7.4,)");
         repoUrl = "https://maven.repository.redhat.com/ga/";
         repo = new RemoteRepository.Builder("mrrc", "default", repoUrl).build();
      } else {
         artifact = new DefaultArtifact("org.wildfly.channels", "wildfly", "channel", "yaml", "[26.1.0,)");
         repoUrl = "https://repo1.maven.org/maven2/";
         repo = new RemoteRepository.Builder("central", "default", repoUrl).build();
      }

      final Artifact resolvedArtifact = resolveChannelFile(artifact, repo);
      final String resolvedChannelFileUrl = resolvedArtifact.getFile().toURI().toURL().toString();
      String gav = resolvedArtifact.getGroupId() + ":" + resolvedArtifact.getArtifactId() + ":" + resolvedArtifact.getVersion();
      ChannelRef channelRef = new ChannelRef("eap", repoUrl, gav, resolvedChannelFileUrl);
      ChannelRef localRef = new ChannelRef("local", loadFile("universe.yaml"));
      ChannelRef universeRef = new ChannelRef("universe", loadFile("galleon.yaml"));

      final List<ChannelRef> channelRefs = Arrays.asList(channelRef, localRef, universeRef);
      return channelRefs;
   }

   public static Artifact resolveChannelFile(DefaultArtifact artifact,
                            RemoteRepository repo) throws VersionRangeResolutionException, MalformedURLException, ArtifactResolutionException {
      final RepositorySystem repositorySystem = newRepositorySystem();
      final DefaultRepositorySystemSession repositorySession = newRepositorySystemSession(repositorySystem);

      final VersionRangeRequest request = new VersionRangeRequest();
      request.setArtifact(artifact);
      request.setRepositories(Arrays.asList(repo));
      final VersionRangeResult versionRangeResult = repositorySystem.resolveVersionRange(repositorySession, request);
      // TODO: pick latest version using Comparator
      final Artifact latestArtifact = artifact.setVersion(versionRangeResult.getHighestVersion().toString());

      final ArtifactRequest artifactRequest = new ArtifactRequest(latestArtifact, null, null);
      return repositorySystem.resolveArtifact(repositorySession, artifactRequest).getArtifact();
   }

   private String loadFile(String name) {
      return Server.class.getResource("/channels/eap/" + name).toString();
   }

   private static RepositorySystem newRepositorySystem() {
      DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
      locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
      locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
      locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
         @Override
         public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
            exception.printStackTrace();
         }
      });
      return locator.getService(RepositorySystem.class);
   }

   private static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
      DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

      String location = System.getProperty("user.home") + "/.m2/repository/";
      LocalRepository localRepo = new LocalRepository(location);
      session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
      return session;
   }

   private static RemoteRepository newRemoteRepository(MavenRepository mavenRepository) {
      return new RemoteRepository.Builder(mavenRepository.getId(), "default", mavenRepository.getUrl().toExternalForm()).build();
   }
}
