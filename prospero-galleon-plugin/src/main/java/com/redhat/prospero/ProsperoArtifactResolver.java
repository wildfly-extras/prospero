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

package com.redhat.prospero;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jboss.galleon.Errors;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.runtime.ProvisioningRuntime;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenErrors;
import org.jboss.galleon.universe.maven.MavenLatestVersionNotAvailableException;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenArtifactVersion;

public class ProsperoArtifactResolver {

   private final List<Channel> channels;
   private RepositorySystem repoSystem;
   private RepositorySystemSession repoSession;
   private Map<String, String> artifactStreams;
   private Map<String, String> resolvedArtifactStreams = new HashMap<>();

   public ProsperoArtifactResolver(Path streamsPath, Path channelFile) throws ProvisioningException {
      System.out.println("Using ProsperoResolver");

      try {
         this.channels = Channel.readChannels(channelFile);
      } catch (IOException e) {
         throw new ProvisioningException(e);
      }

      try {
         repoSystem = newRepositorySystem();
         repoSession = newRepositorySystemSession(repoSystem);
      } catch (IOException e) {
         throw new ProvisioningException(e);
      }

      if(Files.exists(streamsPath)) {
         artifactStreams = readProperties(streamsPath);
      } else {
         artifactStreams = new HashMap<>();
      }
   }

   public ProsperoArtifactResolver(Path streamsPath, String channel) throws ProvisioningException {
      System.out.println("Using ProsperoResolver");

      this.channels = new ArrayList<>();
      this.channels.add(new Channel(channel, "http://localhost:8081/repository/"+channel));

      try {
         repoSystem = newRepositorySystem();
         repoSession = newRepositorySystemSession(repoSystem);
      } catch (IOException e) {
         throw new ProvisioningException(e);
      }

      if(Files.exists(streamsPath)) {
         artifactStreams = readProperties(streamsPath);
      } else {
         artifactStreams = new HashMap<>();
      }
   }

   public void writeManifestFile(ProvisioningRuntime runtime, Map<String, String> mergedArtifactVersions, String channelName) throws MavenUniverseException {
      final File file = runtime.getStagedDir().resolve("manifest.xml").toFile();
      try (final FileWriter fileWriter = new FileWriter(file)) {
         fileWriter.write("<manifest>\n");
         for (Map.Entry<String, String> entry : mergedArtifactVersions.entrySet()) {
            final MavenArtifact artifact = MavenArtifact.fromString(entry.getValue());
            String version = artifact.getExtension(); // something's messed up here?
            if (resolvedArtifactStreams.containsKey(entry.getKey())) {
               version = resolvedArtifactStreams.get(entry.getKey());
            }
            fileWriter.write(String.format("<artifact package=\"%s\" name=\"%s\" version=\"%s\" classifier=\"\"/>%n",
                                           artifact.getGroupId(), artifact.getArtifactId(), version));

         }
         fileWriter.write("</manifest>\n");
      } catch (IOException e) {
         e.printStackTrace();
      }

      // write channels into installation
      final File channelsFile = runtime.getStagedDir().resolve("channels.json").toFile();
      try {
         Channel.writeChannels(channels, channelsFile);
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public void resolve(MavenArtifact artifact) throws MavenUniverseException {
      if (artifact.isResolved()) {
         throw new MavenUniverseException("Artifact is already resolved");
      }

      final String latestVersion = doGetHighestVersion(artifact, null, null, null);
      resolvedArtifactStreams.put(artifact.getGroupId() + ":" + artifact.getArtifactId(), latestVersion);

      final ArtifactRequest request = new ArtifactRequest();
      request.setArtifact(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(),
                                              artifact.getExtension(), latestVersion));
      request.setRepositories(getRepositories());

      final ArtifactResult result;
      try {
         result = repoSystem.resolveArtifact(repoSession, request);
      } catch (Exception e) {
         throw new MavenUniverseException("Failed to resolve " + request.getArtifact().toString(), e);
      }
      if (!result.isResolved()) {
         throw new MavenUniverseException("Failed to resolve " +request.getArtifact().toString());
      }
      if (result.isMissing()) {
         throw new MavenUniverseException("Repository is missing artifact " + request.getArtifact().toString());
      }
      artifact.setVersion(result.getArtifact().getVersion());
      artifact.setPath(Paths.get(result.getArtifact().getFile().toURI()));
   }

   private String doGetHighestVersion(MavenArtifact mavenArtifact, String lowestQualifier, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
      final String artifactStreamId = mavenArtifact.getGroupId() + ":" + mavenArtifact.getArtifactId();
         final MavenArtifact streamDef = MavenArtifact.fromString(artifactStreamId + ":[" + mavenArtifact.getVersion() + ",)");
         final VersionRangeResult rangeResult = getVersionRange(new DefaultArtifact(streamDef.getGroupId(), streamDef.getArtifactId(), streamDef.getExtension(), streamDef.getVersionRange()));
         final MavenArtifactVersion latest = rangeResult == null ? null : MavenArtifactVersion.getLatest(rangeResult.getVersions(), lowestQualifier, includeVersion, excludeVersion);
         if (latest == null) {
            return mavenArtifact.getVersion();
         }
         System.out.println(streamDef.toString() + " == " + latest);
         return latest.toString();
   }

   private VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
      VersionRangeRequest rangeRequest = new VersionRangeRequest();
      rangeRequest.setArtifact(artifact);
      rangeRequest.setRepositories(getRepositories());
      VersionRangeResult rangeResult;
      try {
         rangeResult = repoSystem.resolveVersionRange(repoSession, rangeRequest);
      } catch (VersionRangeResolutionException ex) {
         throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
      }
      return rangeResult;
   }

   private List<RemoteRepository> getRepositories() {
      final ArrayList<RemoteRepository> repos = new ArrayList<>();
      for (Channel channel : channels) {
         RemoteRepository channelRepo = new RemoteRepository.Builder(channel.getName(), "default", channel.getUrl())
            .setReleasePolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, null))
            .setSnapshotPolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, null))
            .build();
         repos.add(channelRepo);
      }
      return repos;
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

      return session;
   }

   private static void readProperties(Path propsFile, Map<String, String> propsMap) throws ProvisioningException {
      try(BufferedReader reader = Files.newBufferedReader(propsFile)) {
         String line = reader.readLine();
         while(line != null) {
            line = line.trim();
            if(line.charAt(0) != '#' && !line.isEmpty()) {
               final int i = line.indexOf('=');
               if(i < 0) {
                  throw new ProvisioningException("Failed to parse property " + line + " from " + propsFile);
               }
               propsMap.put(line.substring(0, i), line.substring(i + 1));
            }
            line = reader.readLine();
         }
      } catch (IOException e) {
         throw new ProvisioningException(Errors.readFile(propsFile), e);
      }
   }

   private static Map<String, String> readProperties(final Path propsFile) throws ProvisioningException {
      final Map<String, String> propsMap = new HashMap<>();
      readProperties(propsFile, propsMap);
      return propsMap;
   }
}
