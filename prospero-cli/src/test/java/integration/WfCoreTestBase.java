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

package integration;

import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.WellKnownRepositories;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.BeforeClass;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class WfCoreTestBase {

    public static final String BASE_VERSION = "19.0.0.Beta11";
    public static final String BASE_JAR = "wildfly-cli-" + BASE_VERSION + ".jar";
    public static final String UPGRADE_VERSION = "19.0.0.Beta12-SNAPSHOT";
    public static final String UPGRADE_JAR = "wildfly-cli-" + UPGRADE_VERSION + ".jar";
    protected final List<RemoteRepository> repositories = defaultRemoteRepositories();
    protected MavenSessionManager mavenSessionManager = new MavenSessionManager(Paths.get(MavenSessionManager.LOCAL_MAVEN_REPO));

    @BeforeClass
    public static void deployUpgrade() throws InstallationException, ArtifactResolutionException {
        final MavenSessionManager msm = new MavenSessionManager(Paths.get(MavenSessionManager.LOCAL_MAVEN_REPO));
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system, false);

        installIfMissing(system, session, "org.wildfly.core", "wildfly-cli", null);
        installIfMissing(system, session, "org.wildfly.core", "wildfly-cli", "client");
    }

    private static void installIfMissing(RepositorySystem system, DefaultRepositorySystemSession session, String groupId, String artifactId, String classifier) throws ArtifactResolutionException, InstallationException {
        final ArtifactRequest artifactRequest = new ArtifactRequest();
        Artifact updateCli = new DefaultArtifact(groupId, artifactId, classifier, "jar", UPGRADE_VERSION);
        artifactRequest.setArtifact(updateCli);
        try {
            system.resolveArtifact(session, artifactRequest);
        } catch (ArtifactResolutionException e) {
            final InstallRequest installRequest = new InstallRequest();
            updateCli = updateCli.setFile(resolveExistingCliArtifact(system, session, groupId, artifactId, classifier));
            installRequest.addArtifact(updateCli);
            system.install(session, installRequest);
        }
    }

    private static File resolveExistingCliArtifact(RepositorySystem system, DefaultRepositorySystemSession session, String groupId, String artifactId, String classifier) throws ArtifactResolutionException {
        final ArtifactRequest artifactRequest = new ArtifactRequest();
        final DefaultArtifact existing = new DefaultArtifact(groupId, artifactId, classifier, "jar", BASE_VERSION);
        artifactRequest.setRepositories(Arrays.asList(WellKnownRepositories.CENTRAL.get()));
        artifactRequest.setArtifact(existing);
        final ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
        return artifactResult.getArtifact().getFile();
    }

    protected ProvisioningDefinition.Builder defaultWfCoreDefinition() {
        return ProvisioningDefinition.builder()
                .setFpl("wildfly-core@maven(org.jboss.universe:community-universe):19.0")
                .setRepositories(repositories);
    }

    public static List<RemoteRepository> defaultRemoteRepositories() {
        return Arrays.asList(
                new RemoteRepository.Builder("maven-central", "default", "https://repo1.maven.org/maven2/").build(),
                new RemoteRepository.Builder("nexus", "default", "https://repository.jboss.org/nexus/content/groups/public-jboss").build(),
                new RemoteRepository.Builder("maven-redhat-ga", "default", "https://maven.repository.redhat.com/ga").build()
        );
    }
}
