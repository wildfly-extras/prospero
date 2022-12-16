/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.it.commonapi;

import org.apache.commons.io.IOUtils;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Repository;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.RepositoryUtils;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.test.MetadataTestUtils;
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
import java.io.FileInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WfCoreTestBase {

    public static final String BASE_VERSION = "19.0.0.Beta11";
    public static final String BASE_JAR = "wildfly-cli-" + BASE_VERSION + ".jar";
    public static final String UPGRADE_VERSION = "19.0.0.Beta12-SNAPSHOT";
    public static final String UPGRADE_JAR = "wildfly-cli-" + UPGRADE_VERSION + ".jar";
    public static final String CHANNEL_BASE_CORE_19 = "manifests/wfcore-19-base.yaml";
    public static final String CHANNEL_FP_UPDATES = "manifests/wfcore-19-upgrade-fp.yaml";
    public static final String CHANNEL_COMPONENT_UPDATES = "manifests/wfcore-19-upgrade-component.yaml";
    public static final Repository REPOSITORY_MAVEN_CENTRAL = new Repository("maven-central", "https://repo1.maven.org/maven2/");
    public static final Repository REPOSITORY_NEXUS = new Repository("nexus", "https://repository.jboss.org/nexus/content/groups/public-jboss");
    public static final Repository REPOSITORY_MRRC_GA = new Repository("maven-redhat-ga", "https://maven.repository.redhat.com/ga");
    public static final VersionResolverFactory CHANNELS_RESOLVER_FACTORY = new VersionResolverFactory(null, null);

    protected static Artifact resolvedUpgradeArtifact;
    protected static Artifact resolvedUpgradeClientArtifact;
    protected Path outputPath;
    protected Path manifestPath;
    protected ProvisioningAction installation;

    protected final List<Repository> repositories = defaultRemoteRepositories();
    protected MavenSessionManager mavenSessionManager = new MavenSessionManager(MavenSessionManager.LOCAL_MAVEN_REPO);

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @BeforeClass
    public static void deployUpgrade() throws InstallationException, ArtifactResolutionException {
        final MavenSessionManager msm = new MavenSessionManager(MavenSessionManager.LOCAL_MAVEN_REPO);
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system, false);

        resolvedUpgradeArtifact = installIfMissing(system, session, "org.wildfly.core", "wildfly-cli", null);
        resolvedUpgradeClientArtifact = installIfMissing(system, session, "org.wildfly.core", "wildfly-cli", "client");
    }

    @Before
    public void setUp() throws Exception {
        outputPath = temp.newFolder().toPath().resolve("test-server");
        manifestPath = outputPath.resolve(MetadataTestUtils.MANIFEST_FILE_PATH);
        installation = new ProvisioningAction(outputPath, mavenSessionManager, new CliConsole());
    }

    private static Artifact installIfMissing(RepositorySystem system, DefaultRepositorySystemSession session, String groupId, String artifactId, String classifier) throws ArtifactResolutionException, InstallationException {
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
        return upgradeArtifact;
    }

    private static File resolveExistingCliArtifact(RepositorySystem system, DefaultRepositorySystemSession session, String groupId, String artifactId, String classifier) throws ArtifactResolutionException {
        final DefaultArtifact existing = new DefaultArtifact(groupId, artifactId, classifier, "jar", BASE_VERSION);
        return resolveArtifact(system, session, existing).getFile();
    }

    protected ProvisioningDefinition.Builder defaultWfCoreDefinition() {
        return ProvisioningDefinition.builder()
                .setFpl("org.wildfly.core:wildfly-core-galleon-pack::zip")
                .setOverrideRepositories(repositories);
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
        artifactRequest.setRepositories(Arrays.asList(toRemoteRepository(REPOSITORY_MAVEN_CENTRAL)));
        artifactRequest.setArtifact(existing);
        final ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
        return artifactResult.getArtifact();
    }

    private static Object toRemoteRepository() {
        return toRemoteRepository(REPOSITORY_MAVEN_CENTRAL);
    }

    public static RemoteRepository toRemoteRepository(Repository repo) {
        return new RemoteRepository.Builder(repo.getId(), "default", repo.getUrl()).build();
    }

    public static List<Repository> defaultRemoteRepositories() {
        return Arrays.asList(
                REPOSITORY_MAVEN_CENTRAL,
                REPOSITORY_NEXUS,
                REPOSITORY_MRRC_GA
        );
    }

    protected URL mockTemporaryRepo(boolean updatesOnly) throws Exception {
        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system);

        final File repo = temp.newFolder();
        if (!updatesOnly) {
            populateTestRepo(repo, system, session);
        }
        final URL repoUrl = repo.toURI().toURL();


        final DeployRequest deployRequest = new DeployRequest();
        deployRequest.addArtifact(resolvedUpgradeArtifact);
        deployRequest.addArtifact(resolvedUpgradeClientArtifact);
        deployRequest.setRepository(RepositoryUtils.toRemoteRepository("test", repoUrl.toString()));
        system.deploy(session, deployRequest);

        return repoUrl;
    }

    private Path populateTestRepo(File repo, RepositorySystem system, DefaultRepositorySystemSession session) throws Exception {
        final List<RemoteRepository> remoteRepositories = defaultRemoteRepositories().stream().map(r -> toRemoteRepository(r)).collect(Collectors.toList());

        // resolve wildfly-core galleon pack zip
        ArtifactRequest request = new ArtifactRequest(
                new DefaultArtifact("org.wildfly.core", "wildfly-core-galleon-pack", "zip", BASE_VERSION),
                remoteRepositories, null);
        final ArtifactResult galleonPackArtifact = system.resolveArtifact(session, request);

        // extract artifact list
        File galleonPackZip = galleonPackArtifact.getArtifact().getFile();
        Map<String, String> artifacts = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(galleonPackZip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if(entry.getName().endsWith("artifact-versions.properties")) {
                    artifacts = parseArtifactList(IOUtils.toString(zis, StandardCharsets.UTF_8).lines());
                }
            }
        }

        // additional artifacts not included in galleon-pack
        artifacts.put("galleon-pack", "org.wildfly.core:wildfly-core-galleon-pack:19.0.0.Beta11::zip");
        artifacts.put("galleon-plugins", "org.wildfly.galleon-plugins:wildfly-galleon-plugins:6.2.1.Final-SNAPSHOT::jar");

        // resolve all dependencies
        final List<ArtifactRequest> requests = artifacts.values().stream()
                .map(s-> parseGav(s))
                .map(a -> new ArtifactRequest(a,
                        remoteRepositories,
                        null))
                .collect(Collectors.toList());

        final List<ArtifactResult> artifactResults = system.resolveArtifacts(session, requests);

        // deploy resolved artifacts to a temporary folder
        final DeployRequest deployRequest = new DeployRequest();
        for (ArtifactResult artifactResult : artifactResults) {
            deployRequest.addArtifact(artifactResult.getArtifact());
        }
        final Path path = repo.toPath();
        deployRequest.setRepository(RepositoryUtils.toRemoteRepository("test", path.toUri().toURL().toExternalForm()));
        system.deploy(session, deployRequest);
        return path;
    }

    private static Map<String, String> parseArtifactList(java.util.stream.Stream<String> lines) {
        Map<String, String> variables = new HashMap<>();
        final Iterator<String> iterator = lines.iterator();
        while (iterator.hasNext()) {
            final String line = iterator.next();
            final int i = line.indexOf('=');
            if (i < 0) {
                throw new IllegalArgumentException("Failed to locate '=' character in " + line);
            }
            variables.put(line.substring(0, i), line.substring(i + 1));
        }
        return variables;
    }

    private DefaultArtifact parseGav(String gavString) {
        final String[] gav = gavString.split(":");
        if (gav.length < 3) {
            throw new IllegalArgumentException("Wrong format " + gavString);
        }
        return new DefaultArtifact(gav[0], gav[1], gav[3], gav[4], gav[2]);
    }
}
