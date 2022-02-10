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

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
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

public class Server {

   private final String fpl;
   private final List<ChannelRef> channels;

   public Server(String fpl, Path channelsFile) throws IOException {
      if (fpl.equals("eap")) {
         this.fpl = "org.jboss.eap:wildfly-ee-galleon-pack";

         if (channelsFile == null) {
            final DefaultArtifact artifact = new DefaultArtifact("org.wildfly.channels", "eap-74", "channel", "yaml", "[7.4,)");
//            final String repoUrl = "https://maven.repository.redhat.com/ga/";
            final String repoUrl = "http://lacrosse.corp.redhat.com/~bspyrkos/tmp-repo";
            final RemoteRepository repo = new RemoteRepository.Builder("mrrc", "default", repoUrl).build();
            this.channels = readLatestChannelFromMaven(artifact, repoUrl, repo);
         } else {
            this.channels = ChannelRef.readChannels(channelsFile);
         }
      } else if (fpl.equals("wildfly")) {
         this.fpl = "org.wildfly:wildfly-ee-galleon-pack";

         if (channelsFile == null) {
            final DefaultArtifact artifact = new DefaultArtifact("org.wildfly.channels", "wildfly", "channel", "yaml", "[26.1.0,)");
//            final String repoUrl = "http://lacrosse.corp.redhat.com/~bspyrkos/tmp-repo";
            final String repoUrl = "http://lacrosse.corp.redhat.com/~bspyrkos/tmp-repo";
            final RemoteRepository repo = new RemoteRepository.Builder("central", "default", repoUrl).build();
            this.channels = readLatestChannelFromMaven(artifact, repoUrl, repo);
         } else {
            this.channels = ChannelRef.readChannels(channelsFile);
         }
      } else {
         this.fpl = fpl;
         this.channels = ChannelRef.readChannels(channelsFile);
      }
   }

   public String getFpl() {
      return fpl;
   }

   public List<ChannelRef> getChannelRefs() throws IOException {
      return channels;
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
      if (versionRangeResult == null) {
         System.out.println("No version found for " + artifact);
      }
      if (artifact == null) {
         System.out.println("No artifact found for " + artifact);
      }
      if (versionRangeResult.getHighestVersion() == null) {
         System.out.println("No highest version found for " + artifact);
      }
      final Artifact latestArtifact = artifact.setVersion(versionRangeResult.getHighestVersion().toString());

      final ArtifactRequest artifactRequest = new ArtifactRequest(latestArtifact, Arrays.asList(repo), null);
      return repositorySystem.resolveArtifact(repositorySession, artifactRequest).getArtifact();
   }

   protected List<ChannelRef> readLatestChannelFromMaven(DefaultArtifact artifact,
                                                         String repoUrl,
                                                         RemoteRepository repo) throws IOException {
      try {
         final Artifact resolvedArtifact = resolveChannelFile(artifact, repo);
         final String resolvedChannelFileUrl = resolvedArtifact.getFile().toURI().toURL().toString();
         String gav = resolvedArtifact.getGroupId() + ":" + resolvedArtifact.getArtifactId() + ":" + resolvedArtifact.getVersion();
         ChannelRef channelRef = new ChannelRef("eap", repoUrl, gav, resolvedChannelFileUrl);
         ChannelRef localRef = new ChannelRef("local", this.loadFile("universe.yaml"));
         ChannelRef universeRef = new ChannelRef("universe", loadFile("galleon.yaml"));

         return Arrays.asList(channelRef, localRef, universeRef);
      } catch (MalformedURLException | VersionRangeResolutionException | ArtifactResolutionException e) {
         throw new IOException(e);
      }
   }

   protected String loadFile(String name) {
      return Server.class.getResource("/channels/eap/" + name).toString();
   }

   protected static RepositorySystem newRepositorySystem() {
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
}
