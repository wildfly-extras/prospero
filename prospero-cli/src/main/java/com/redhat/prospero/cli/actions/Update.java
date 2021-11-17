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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import com.redhat.prospero.api.ArtifactUtils;
import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.impl.repository.curated.ChannelBuilder;
import com.redhat.prospero.maven.MavenUtils;
import com.redhat.prospero.model.XmlException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.FeaturePackUpdatePlan;
import org.jboss.galleon.layout.ProvisioningPlan;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;

public class Update implements AutoCloseable {

    private final InstallationMetadata metadata;
    private final Repository repository;
    private final ChannelMavenArtifactRepositoryManager maven;
    private final ProvisioningManager provMgr;
    private final Path installDir;
    private final boolean quiet;

    public Update(Path installDir, boolean quiet) throws IOException, ProvisioningException, MetadataException {
        this.metadata = new InstallationMetadata(installDir);
        this.installDir = installDir;
        final RepositorySystem repoSystem = MavenUtils.defaultRepositorySystem();
        final DefaultRepositorySystemSession session = MavenUtils.getDefaultRepositorySystemSession(repoSystem);
        final ChannelBuilder channelBuilder = new ChannelBuilder(repoSystem, session);
        this.repository = channelBuilder
                .setChannels(metadata.getChannels())
                .build();
        this.maven = new ChannelMavenArtifactRepositoryManager(
                repoSystem, session, Channel.readChannels((installDir.resolve(InstallationMetadata.CHANNELS_FILE_NAME))));
        this.provMgr = GalleonUtils.getProvisioningManager(installDir, maven);
        this.quiet = quiet;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Not enough parameters. Need to provide WFLY installation.");
            return;
        }
        final String base = args[0];
        if (args.length == 3) {
            throw new UnsupportedOperationException("Single artifact updates are not supported.");
        }

        try(Update update = new Update(Paths.get(base), false)) {
            update.doUpdateAll();
        }
    }

    public void doUpdateAll() throws ArtifactNotFoundException, XmlException, ProvisioningException, IOException, MetadataException {
        final List<ArtifactChange> updates = new ArrayList<>();
        final Manifest manifest = metadata.getManifest();
        for (Artifact artifact : manifest.getArtifacts()) {
            updates.addAll(findUpdates(artifact.getGroupId(), artifact.getArtifactId()));
        }
        final ProvisioningPlan fpUpdates = findFPUpdates();

        if (updates.isEmpty() && fpUpdates.isEmpty()) {
            System.out.println("No updates to execute");
            return;
        }

        if (!fpUpdates.isEmpty()) {
            System.out.println("Feature pack updates:");
            for (FeaturePackUpdatePlan update : fpUpdates.getUpdates()) {
                final FeaturePackLocation oldFp = update.getInstalledLocation();
                final FeaturePackLocation newFp = update.getNewLocation();
                System.out.println(newFp.getProducerName() + "   " + oldFp.getBuild() + "  ==>  " + newFp.getBuild());
            }
        }

        if (!updates.isEmpty()) {
            System.out.println("Artefact updates found: ");
            for (ArtifactChange update : updates) {
                System.out.println(update);
            }
        }

        if (!quiet) {
            System.out.print("Continue with update [y/n]: ");
            Scanner sc = new Scanner(System.in);
            while (true) {
                String resp = sc.nextLine();
                if (resp.equalsIgnoreCase("n")) {
                    System.out.println("Update cancelled");
                    return;
                } else if (resp.equalsIgnoreCase("y")) {
                    System.out.println("Applying updates");
                    break;
                } else {
                    System.out.print("Choose [y/n]: ");
                }
            }
        }

        applyFpUpdates(fpUpdates);

        metadata.writeFiles();

        System.out.println("Done");
    }

    @Override
    public void close() throws Exception {
        this.maven.close();
    }

    private ProvisioningPlan findFPUpdates() throws ProvisioningException, IOException {
        return provMgr.getUpdates(true);
    }

    private Set<Artifact> applyFpUpdates(ProvisioningPlan updates) throws ProvisioningException, IOException {
        provMgr.apply(updates);

        final Set<MavenArtifact> resolvedArtfacts = maven.resolvedArtfacts();

        // filter out non-installed artefacts
        final Set<Artifact> collected = resolvedArtfacts.stream()
                .map(a->new DefaultArtifact(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion()))
                .filter(a->(!a.getArtifactId().equals("wildfly-producers") && !a.getArtifactId().equals("community-universe")))
                .collect(Collectors.toSet());
        metadata.registerUpdates(collected);
        return collected;
    }

    public List<ArtifactChange> findUpdates(String groupId, String artifactId) throws ArtifactNotFoundException, XmlException {
        List<ArtifactChange> updates = new ArrayList<>();

        final Manifest manifest = metadata.getManifest();

        final Artifact artifact = manifest.find(new DefaultArtifact(groupId, artifactId, "", ""));

        if (artifact == null) {
            throw new ArtifactNotFoundException(String.format("Artifact [%s:%s] not found", groupId, artifactId));
        }

        final Artifact latestVersion = repository.resolveLatestVersionOf(artifact);

        if (latestVersion == null || ArtifactUtils.compareVersion(latestVersion, artifact) <= 0) {
            return updates;
        }

        updates.add(new ArtifactChange(artifact, latestVersion));

        return updates;
    }

}
