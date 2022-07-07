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

package org.wildfly.prospero.it.commonapi;

import org.eclipse.aether.installation.InstallResult;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.BeforeClass;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WfCoreTestBase {

    public static final String BASE_VERSION = "19.0.0.Beta11";
    public static final String BASE_JAR = "wildfly-cli-" + BASE_VERSION + ".jar";
    public static final String UPGRADE_VERSION = "19.0.0.Beta12-SNAPSHOT";
    public static final String UPGRADE_JAR = "wildfly-cli-" + UPGRADE_VERSION + ".jar";
    public static final String CHANNEL_BASE_CORE_19 = "channels/wfcore-19-base.yaml";
    public static final String CHANNEL_FP_UPDATES = "channels/wfcore-19-upgrade-fp.yaml";
    public static final String CHANNEL_COMPONENT_UPDATES = "channels/wfcore-19-upgrade-component.yaml";
    public static final RepositoryRef REPOSITORY_MAVEN_CENTRAL = new RepositoryRef("maven-central", "https://repo1.maven.org/maven2/");
    public static final RepositoryRef REPOSITORY_NEXUS = new RepositoryRef("nexus", "https://repository.jboss.org/nexus/content/groups/public-jboss");
    public static final RepositoryRef REPOSITORY_MRRC_GA = new RepositoryRef("maven-redhat-ga", "https://maven.repository.redhat.com/ga");
    protected static Artifact resolvedUpgradeArtifact;

    protected final List<RepositoryRef> repositories = defaultRemoteRepositories();
    protected MavenSessionManager mavenSessionManager = new MavenSessionManager(MavenSessionManager.LOCAL_MAVEN_REPO);

    @BeforeClass
    public static void deployUpgrade() throws InstallationException, ArtifactResolutionException {
        final MavenSessionManager msm = new MavenSessionManager(MavenSessionManager.LOCAL_MAVEN_REPO);
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system, false);

        installIfMissing(system, session, "org.wildfly.core", "wildfly-cli", null);
        installIfMissing(system, session, "org.wildfly.core", "wildfly-cli", "client");
    }

    private static void installIfMissing(RepositorySystem system, DefaultRepositorySystemSession session, String groupId, String artifactId, String classifier) throws ArtifactResolutionException, InstallationException {
        final ArtifactRequest artifactRequest = new ArtifactRequest();
        Artifact updateCli = new DefaultArtifact(groupId, artifactId, classifier, "jar", UPGRADE_VERSION);
        artifactRequest.setArtifact(updateCli);
        Artifact upgradeArtifact;
        try {
            final ArtifactResult result = system.resolveArtifact(session, artifactRequest);
            upgradeArtifact = result.getArtifact();
        } catch (ArtifactResolutionException e) {
            final InstallRequest installRequest = new InstallRequest();
            updateCli = updateCli.setFile(resolveExistingCliArtifact(system, session, groupId, artifactId, classifier));
            installRequest.addArtifact(updateCli);
            final InstallResult result = system.install(session, installRequest);
            upgradeArtifact = result.getArtifacts().stream().findFirst().get();
        }
        resolvedUpgradeArtifact = upgradeArtifact;
    }

    private static File resolveExistingCliArtifact(RepositorySystem system, DefaultRepositorySystemSession session, String groupId, String artifactId, String classifier) throws ArtifactResolutionException {
        final DefaultArtifact existing = new DefaultArtifact(groupId, artifactId, classifier, "jar", BASE_VERSION);
        return resolveArtifact(system, session, existing).getFile();
    }

    protected ProvisioningDefinition.Builder defaultWfCoreDefinition() {
        return ProvisioningDefinition.builder()
                .setFpl("wildfly-core@maven(org.jboss.universe:community-universe):19.0")
                .setRemoteRepositories(repositories.stream().map(RepositoryRef::getUrl).collect(Collectors.toList()));
    }

    protected Artifact resolveArtifact(String groupId, String artifactId, String version) throws ArtifactResolutionException {
        final MavenSessionManager msm = new MavenSessionManager(MavenSessionManager.LOCAL_MAVEN_REPO);
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system, false);

        final DefaultArtifact existing = new DefaultArtifact(groupId, artifactId, null, "jar", version);
        return resolveArtifact(system, session, existing);
    }

    private static Artifact resolveArtifact(RepositorySystem system, DefaultRepositorySystemSession session, DefaultArtifact existing) throws ArtifactResolutionException {
        final ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setRepositories(Arrays.asList(REPOSITORY_MRRC_GA.toRemoteRepository()));
        artifactRequest.setArtifact(existing);
        final ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
        return artifactResult.getArtifact();
    }

    public static List<RepositoryRef> defaultRemoteRepositories() {
        return Arrays.asList(
                REPOSITORY_MAVEN_CENTRAL,
                REPOSITORY_NEXUS,
                REPOSITORY_MRRC_GA
        );
    }
}
