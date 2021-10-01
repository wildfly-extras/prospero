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

package com.redhat.prospero.cli.actions;

import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.installation.Modules;
import com.redhat.prospero.xml.ManifestXmlSupport;
import com.redhat.prospero.xml.XmlException;
import org.eclipse.aether.artifact.Artifact;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;

import static com.redhat.prospero.api.ArtifactUtils.from;

public class GalleonProvision {

    static {
        enableJBossLogManager();
    }

    private static void enableJBossLogManager() {
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        }
    }

    public static void main(String[] args) throws ProvisioningException, IOException {
        if (args.length < 3) {
            System.out.println("Not enough parameters. Need to provide FPL, output directory and channels file.");
            return;
        }
        final String fpl = args[0];
        final String base = args[1];
        final String channelsFile = args[2];

        new GalleonProvision().installFeaturePack(fpl, base, channelsFile);
    }

    public void installFeaturePack(String fpl, String path, String channelsFile) throws ProvisioningException, IOException {
        Path installDir = Paths.get(path);
        if (Files.exists(installDir)) {
            throw new ProvisioningException("Installation dir " + installDir + " already exists");
        }
        try (final ChannelMavenArtifactRepositoryManager maven
                = GalleonUtils.getChannelRepositoryManager(readChannels(Paths.get(channelsFile)), GalleonUtils.newRepositorySystem())) {
            ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, maven);
            FeaturePackLocation loc = FeaturePackLocation.fromString(fpl);
            provMgr.install(loc);
            writeProsperoMetadata(installDir, maven, Paths.get(channelsFile));
        }
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, Path path) throws ProvisioningException {
            final Modules modules = new Modules(home);
            Set<MavenArtifact> installed = new HashSet<>();
            for (MavenArtifact resolvedArtifact : maven.resolvedArtfacts()) {
                if (containsArtifact(resolvedArtifact, modules)) {
                    installed.add(resolvedArtifact);
                }
            }
            writeManifestFile(home, installed, readChannels(path));
        }

        private boolean containsArtifact(MavenArtifact resolvedArtifact, Modules modules) {
            return !modules.find(from(resolvedArtifact)).isEmpty();
        }

        private void writeManifestFile(Path home, Set<MavenArtifact> artifactSet, List<Channel> channels) throws ProvisioningException {
            List<Artifact> artifacts = new ArrayList<>();
            for (MavenArtifact artifact : artifactSet) {
                artifacts.add(from(artifact));
            }

            try {
                ManifestXmlSupport.write(new Manifest(artifacts, home.resolve("manifest.xml")));
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

    private static List<Channel> readChannels(Path channelFile) throws ProvisioningException {
        try {
            return Channel.readChannels(channelFile);
        } catch (IOException e) {
            throw new ProvisioningException(e);
        }
    }
}
