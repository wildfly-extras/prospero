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

package com.redhat.prospero.actions;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class BootstrapMavenResolverFactory implements MavenVersionsResolver.Factory {

   static String LOCAL_MAVEN_REPO = System.getProperty("user.home") + "/.m2/repository";

   @Override
   public MavenVersionsResolver create(List<MavenRepository> mavenRepositories, boolean resolveLocalCache) {
      final List<RemoteRepository> remoteRepositories = mavenRepositories.stream().map(BootstrapMavenResolverFactory::newRemoteRepository).collect(Collectors.toList());
      final RepositorySystem system = newRepositorySystem();
      final DefaultRepositorySystemSession session = newRepositorySystemSession(system, true);
      return new MavenVersionsResolver() {

         @Override
         public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
            return null;
         }

         @Override
         public File resolveArtifact(String groupId,
                                     String artifactId,
                                     String extension,
                                     String classifier,
                                     String version) throws UnresolvedMavenArtifactException {
            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);

            ArtifactRequest request = new ArtifactRequest();
            request.setArtifact(artifact);
            request.setRepositories(remoteRepositories);
            try {
               ArtifactResult result = system.resolveArtifact(session, request);
               return result.getArtifact().getFile();
            } catch (ArtifactResolutionException e) {
               UnresolvedMavenArtifactException umae = new UnresolvedMavenArtifactException();
               umae.initCause(e);
               throw umae;
            }
         }
      };
   }

   public static RepositorySystem newRepositorySystem() {
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

   public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, boolean resolveLocalCache) {
      DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

      String location;
      if (resolveLocalCache) {
         location = LOCAL_MAVEN_REPO;
      } else {
         location = "target/local-repo" ;
      }
      LocalRepository localRepo = new LocalRepository(location);
      session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
      return session;
   }

   private static RemoteRepository newRemoteRepository(MavenRepository mavenRepository) {
      return new RemoteRepository.Builder(mavenRepository.getId(), "default", mavenRepository.getUrl().toExternalForm()).build();
   }
}
