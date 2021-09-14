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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.impl.repository.MavenRepository;
import com.redhat.prospero.xml.ManifestXmlSupport;
import com.redhat.prospero.xml.XmlException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.runtime.ProvisioningRuntime;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;

public class ProsperoArtifactResolver {

   private final List<Channel> channels;
   private Map<String, String> resolvedArtifactStreams = new HashMap<>();
   private MavenRepository repository;

   public ProsperoArtifactResolver(Path channelFile) throws ProvisioningException {
      this(readChannels(channelFile));
   }

   public ProsperoArtifactResolver(String channelUrl) throws ProvisioningException {
      this(Arrays.asList(new Channel("prospero", channelUrl)));
   }

   private ProsperoArtifactResolver(List<Channel> channels) throws ProvisioningException {
      System.out.println("Using ProsperoResolver");

      this.channels = channels;
      repository = new MavenRepository(channels);
   }

   private static List<Channel> readChannels(Path channelFile) throws ProvisioningException {
      try {
         return Channel.readChannels(channelFile);
      } catch (IOException e) {
         throw new ProvisioningException(e);
      }
   }

   public void writeManifestFile(ProvisioningRuntime runtime, Map<String, String> mergedArtifactVersions, String channelName) throws MavenUniverseException {
      List<com.redhat.prospero.api.Artifact> artifacts = new ArrayList<>();
      for (Map.Entry<String, String> entry : mergedArtifactVersions.entrySet()) {
         final MavenArtifact artifact = MavenArtifact.fromString(entry.getValue());
         String version = artifact.getExtension(); // something's messed up here?
         if (resolvedArtifactStreams.containsKey(entry.getKey())) {
            version = resolvedArtifactStreams.get(entry.getKey());
         }
         artifacts.add(new com.redhat.prospero.api.Artifact(artifact.getGroupId(), artifact.getArtifactId(), version, ""));

      }

      try {
         ManifestXmlSupport.write(new Manifest(artifacts, Collections.emptyList(), runtime.getStagedDir().resolve("manifest.xml")));
      } catch (XmlException e) {
         e.printStackTrace();
      }

      // write channels into installation
      final File channelsFile = runtime.getStagedDir().resolve("channels.json").toFile();
      try {
         com.redhat.prospero.api.Channel.writeChannels(channels, channelsFile);
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public void resolve(MavenArtifact artifact) throws MavenUniverseException {

      if (artifact.isResolved()) {
         throw new MavenUniverseException("Artifact is already resolved");
      }
      final com.redhat.prospero.api.Artifact prosperoArtifact = new com.redhat.prospero.api.Artifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getClassifier());
      final String latestVersion = repository.findLatestVersionOf(prosperoArtifact).getVersion();
      final MavenArtifact streamDef = MavenArtifact.fromString(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":[" + artifact.getVersion() + ",)");
      System.out.println(streamDef + " == " + latestVersion);

      resolvedArtifactStreams.put(artifact.getGroupId() + ":" + artifact.getArtifactId(), latestVersion);

      try {
         final File resolvedPath = repository.resolve(prosperoArtifact.newVersion(latestVersion));
         artifact.setVersion(latestVersion);
         artifact.setPath(resolvedPath.toPath());
      } catch (ArtifactNotFoundException e) {
         throw new MavenUniverseException(e.getMessage(), e);
      }
   }
}
