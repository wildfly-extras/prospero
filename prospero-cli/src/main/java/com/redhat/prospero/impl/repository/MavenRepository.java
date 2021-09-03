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

package com.redhat.prospero.impl.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.redhat.prospero.api.Artifact;
import com.redhat.prospero.api.ArtifactDependencies;
import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Gav;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.xml.XmlException;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;

public class MavenRepository implements Repository {

   public static void main(String[] args) throws Exception {
//      System.out.println(new MavenRepository().resolve(new Artifact("io.undertow", "undertow-core", "2.2.7.Final", "")));
      System.out.println(new MavenRepository().findLatestVersionOf(new Artifact("io.undertow", "undertow-core", "2.2.7.Final", "")));
   }

   private final RepositorySystem repoSystem;
   private final RepositorySystemSession repoSession;

   public MavenRepository() {
      try {
         repoSystem = newRepositorySystem();
         repoSession = newRepositorySystemSession(repoSystem );
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public File resolve(Gav artifact) throws ArtifactNotFoundException {
      ArtifactRequest req = new ArtifactRequest();
      req.setArtifact(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getPackaging(), artifact.getVersion()));
      req.setRepositories(newRepositories(repoSystem, repoSession));
      try {
         final ArtifactResult artifactResult = repoSystem.resolveArtifact(repoSession, req);
         return artifactResult.getArtifact().getFile();
      } catch (ArtifactResolutionException e) {
         throw new ArtifactNotFoundException("Unable to find artifact [" + artifact + "]", e);
      }
   }

   @Override
   public Gav findLatestVersionOf(Gav artifact) {
      VersionRangeRequest req = new VersionRangeRequest();
      final DefaultArtifact artifact1 = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getPackaging(), "[" + artifact.getVersion() + ",)");
      req.setArtifact(artifact1);
      req.setRepositories(newRepositories(repoSystem, repoSession));

      try {
         final VersionRangeResult versionRangeResult = repoSystem.resolveVersionRange(repoSession, req);
         final Version highestVersion = versionRangeResult.getHighestVersion();
         if (highestVersion == null) {
            // TODO: fix the zip artifacts
//            System.out.println("Artifact not found: [" + artifact + "]");
            return artifact;
         } else {
            return artifact.newVersion(highestVersion.toString());
         }
      } catch (VersionRangeResolutionException e) {
         e.printStackTrace();
         return null;
      }
   }

   @Override
   public ArtifactDependencies resolveDescriptor(Gav latestVersion) throws XmlException {
      return null;
   }

   private static RepositorySystem newRepositorySystem()
   {
      /*
       * Aether's components implement org.eclipse.aether.spi.locator.Service to ease manual wiring and using the
       * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter
       * factories.
       */
      DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
      locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class );
      locator.addService(TransporterFactory.class, FileTransporterFactory.class );
      locator.addService( TransporterFactory.class, HttpTransporterFactory.class );

      locator.setErrorHandler( new DefaultServiceLocator.ErrorHandler()
      {
         @Override
         public void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception )
         {
            System.out.println(String.format("Service creation failed for %s with implementation %s",
                                             type, impl ));
            exception.printStackTrace();
         }
      } );

      return locator.getService( RepositorySystem.class );
   }

   private static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system ) throws IOException {
      DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

      org.eclipse.aether.repository.LocalRepository localRepo = new LocalRepository(Files.createTempDirectory("mvn-repo").toString() );
      session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepo ) );

      //      session.setTransferListener( new ConsoleTransferListener() );
      //      session.setRepositoryListener( new ConsoleRepositoryListener() );

      // uncomment to generate dirty trees
      // session.setDependencyGraphTransformer( null );

      return session;
   }

   public static List<RemoteRepository> newRepositories(RepositorySystem system, RepositorySystemSession session )
   {
      return new ArrayList<>(Collections.singletonList(newCentralRepository() ) );
   }

   private static RemoteRepository newCentralRepository()
   {
      //      return new RemoteRepository.Builder( "central", "default", "https://repo.maven.apache.org/maven2/" ).build();
      //      return new RemoteRepository.Builder( "mrrc", "default", "https://maven.repository.redhat.com/earlyaccess/all/" ).build();
      //      return new RemoteRepository.Builder( "mrrc", "default", "https://maven.repository.redhat.com/ga" ).build();
      return new RemoteRepository.Builder( "dev", "default", "http://localhost:8081/repository/dev/" ).build();
   }
}
