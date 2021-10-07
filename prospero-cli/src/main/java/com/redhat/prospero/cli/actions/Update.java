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
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import com.redhat.prospero.api.ArtifactUtils;
import com.redhat.prospero.cli.MavenFallback;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.api.PackageInstallationException;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.impl.repository.curated.ChannelBuilder;
import com.redhat.prospero.installation.LocalInstallation;
import com.redhat.prospero.installation.Modules;
import com.redhat.prospero.model.ManifestXmlSupport;
import com.redhat.prospero.model.XmlException;
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

    private final LocalInstallation localInstallation;
    private final Repository repository;
    private final ChannelMavenArtifactRepositoryManager maven;
    private final ProvisioningManager provMgr;

    public Update(Path installDir) throws XmlException, IOException, ProvisioningException {
        this.localInstallation = new LocalInstallation(installDir);
        final RepositorySystem repoSystem = GalleonUtils.newRepositorySystem();
        final ChannelBuilder channelBuilder = new ChannelBuilder(repoSystem, MavenFallback.getDefaultRepositorySystemSession(repoSystem));
        this.repository = channelBuilder.buildChannelRepository(localInstallation.getChannels().get(0));
        this.maven = GalleonUtils.getChannelRepositoryManager(readChannels(installDir.resolve("channels.json")), repoSystem);
        this.provMgr = GalleonUtils.getProvisioningManager(installDir, maven);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Not enough parameters. Need to provide WFLY installation.");
            return;
        }
        final String base = args[0];
        final String artifact;
        if (args.length == 3) {
            // TODO: take multiple artifacts
            artifact = args[2];
        } else {
            artifact = null;
        }

        try(Update update = new Update(Paths.get(base))) {
            if (artifact == null) {
                update.doUpdateAll(Paths.get(base));
            } else {
                update.doUpdate(artifact.split(":")[0], artifact.split(":")[1]);
            }
        }
    }

    public void doUpdateAll(Path installationDir) throws ArtifactNotFoundException, XmlException, PackageInstallationException, ProvisioningException, IOException {
        final List<UpdateAction> updates = new ArrayList<>();
        for (Artifact artifact : localInstallation.getManifest().getArtifacts()) {
            updates.addAll(findUpdates(artifact.getGroupId(), artifact.getArtifactId()));
        }
        final ProvisioningPlan fpUpdates = findFPUpdates(installationDir);

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
            for (UpdateAction update : updates) {
                System.out.println(update);
            }
        }

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

        final Set<Artifact> updated;
        if (!fpUpdates.isEmpty()) {
            updated = applyFpUpdates(fpUpdates, installationDir);
        } else {
            updated = Collections.emptySet();
        }

        final Artifact artifact = updated.stream().filter(a -> a.getArtifactId().equals("wildfly-cli")).findFirst().get();
        for (UpdateAction update : updates) {
            artifact.equals(update.newVersion);
            if (!updated.contains(update.newVersion)) {
                localInstallation.updateArtifact(update.oldVersion, update.newVersion, update.newVersion.getFile());
            }
        }

        ManifestXmlSupport.write(localInstallation.getManifest());

        System.out.println("Done");
    }

    @Override
    public void close() throws Exception {
        this.maven.close();
    }

    private ProvisioningPlan findFPUpdates(Path installDir) throws ProvisioningException, IOException {
        return provMgr.getUpdates(true);
    }

    private Set<Artifact> applyFpUpdates(ProvisioningPlan updates, Path installDir) throws ProvisioningException, IOException {
        provMgr.apply(updates);

        final Set<MavenArtifact> resolvedArtfacts = maven.resolvedArtfacts();

        // filter out non-installed artefacts
        final Modules modules = new Modules(installDir);
        final Set<Artifact> collected = resolvedArtfacts.stream()
                .map(a->new DefaultArtifact(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion()))
                .filter(a -> !(modules.find(a)).isEmpty())
                .collect(Collectors.toSet());
        localInstallation.registerUpdates(collected);
        return collected;
    }

    public void doUpdate(String groupId, String artifactId) throws ArtifactNotFoundException, XmlException, PackageInstallationException {
        final List<UpdateAction> updates = findUpdates(groupId, artifactId);
        if (updates.isEmpty()) {
            System.out.println("No updates to execute");
            return;
        }

        System.out.println("Updates found: ");
        for (UpdateAction update : updates) {
            System.out.print(update + "\t\t\t\t\t");

            localInstallation.updateArtifact(update.oldVersion, update.newVersion, update.newVersion.getFile());

            System.out.println("DONE");
        }

        ManifestXmlSupport.write(localInstallation.getManifest());
    }

    public List<UpdateAction> findUpdates(String groupId, String artifactId) throws ArtifactNotFoundException, XmlException {
        List<UpdateAction> updates = new ArrayList<>();

        final Manifest manifest = localInstallation.getManifest();

        final Artifact artifact = manifest.find(new DefaultArtifact(groupId, artifactId, "", ""));

        if (artifact == null) {
            throw new ArtifactNotFoundException(String.format("Artifact [%s:%s] not found", groupId, artifactId));
        }

        final Artifact latestVersion = repository.resolveLatestVersionOf(artifact);

        if (latestVersion == null || ArtifactUtils.compareVersion(latestVersion, artifact) <= 0) {
            return updates;
        }

        updates.add(new UpdateAction(artifact, latestVersion));

        return updates;
    }

    private static List<Channel> readChannels(Path channelFile) throws ProvisioningException {
        try {
            return Channel.readChannels(channelFile);
        } catch (IOException e) {
            throw new ProvisioningException(e);
        }
    }

    class UpdateAction {
        private Artifact oldVersion;
        private Artifact newVersion;

        public UpdateAction(Artifact oldVersion, Artifact newVersion) {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }

        public Artifact getNewVersion() {
            return newVersion;
        }

        public Artifact getOldVersion() {
            return oldVersion;
        }

        @Override
        public String toString() {
            return String.format("Update [%s, %s]:\t\t %s ==> %s", oldVersion.getGroupId(), oldVersion.getArtifactId(),
                    oldVersion.getVersion(), newVersion.getVersion());
        }
    }
}
