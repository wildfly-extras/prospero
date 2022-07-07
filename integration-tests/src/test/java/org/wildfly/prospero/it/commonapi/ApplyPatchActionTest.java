package org.wildfly.prospero.it.commonapi;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.jboss.galleon.ProvisioningException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.actions.ApplyPatchAction;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.patch.PatchArchive;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.wildfly.prospero.actions.ApplyPatchAction.PATCHES_REPO_PATH;
import static org.wildfly.prospero.actions.ApplyPatchAction.PATCH_REPO_NAME;

public class ApplyPatchActionTest extends WfCoreTestBase {

    public static final String PATCHED_ARTIFACT_ID = "wildfly-controller";
    public static final String PATCHED_ARTIFACT_GROUP = "org.wildfly.core";
    public static final String PATCHED_ARTIFACT_VERSION = "123.1.1";
    public static final String PATCHED_ARTIFACT_FILENAME = String.format("%s-%s.jar", PATCHED_ARTIFACT_ID, PATCHED_ARTIFACT_VERSION);
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static final String OUTPUT_DIR = "target/server";
    private static final Path OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();
    private final ProvisioningAction installation = new ProvisioningAction(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole());
    private Path provisionConfigFile;

    @Before
    public void setUp() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (OUTPUT_PATH.toFile().exists()) {
            FileUtils.deleteDirectory(OUTPUT_PATH.toFile());
            OUTPUT_PATH.toFile().delete();
        }
    }

    @Test
    public void testInstallSimplePatch() throws Exception {
        // use artifact not in LOCAL_MAVEN_REPO to make sure patch repository is being used
        final Artifact mockArtifact = mockUpgradeArtifact();

        // build patch zip
        final File patchArchive = temp.newFile("patch-test.zip");
        PatchArchive.createPatchArchive(
                Collections.singletonList(mockArtifact),
                patchArchive,
                "patch-test00001");

        // installCore
        provisionConfigFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);
        Path installDir = Paths.get(OUTPUT_PATH.toString());
        if (Files.exists(installDir)) {
            throw new ProvisioningException("Installation dir " + installDir + " already exists");
        }

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        installation.provision(provisioningDefinition);

        // apply patch
        final ApplyPatchAction applyPatchAction = new ApplyPatchAction(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole());
        applyPatchAction.apply(patchArchive.toPath());

        // verify config changed - patch channel & local repository
        final Path metadataDir = installDir.resolve(InstallationMetadata.METADATA_DIR);
        final ProsperoConfig prosperoConfig = ProsperoConfig.readConfig(metadataDir.resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME));
        assertThat(prosperoConfig.getChannels()).containsExactly(
                new ChannelRef(null, installDir.resolve(".patches").resolve("patch-test00001-channel.yaml").toUri().toURL().toString()),
                new ChannelRef(null, ApplyPatchActionTest.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19).toString())
        );

        assertThat(prosperoConfig.getRepositories()).contains(
                new RepositoryRef(PATCH_REPO_NAME, installDir.resolve(PATCHES_REPO_PATH).toUri().toURL().toString())
        );
        // verify artifact changed in manifest
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest(metadataDir.resolve(InstallationMetadata.MANIFEST_FILE_NAME),
                PATCHED_ARTIFACT_GROUP, PATCHED_ARTIFACT_ID);
        assertEquals(PATCHED_ARTIFACT_VERSION, wildflyCliArtifact.get().getVersion());
    }

    private Artifact mockUpgradeArtifact() throws IOException, ArtifactResolutionException {
        final File target = temp.newFolder();
        final Path mockArtifactFile = target.toPath().resolve(PATCHED_ARTIFACT_FILENAME);
        final Artifact artifact = resolveArtifact(PATCHED_ARTIFACT_GROUP, PATCHED_ARTIFACT_ID, WfCoreTestBase.BASE_VERSION);
        Files.copy(artifact.getFile().toPath(), mockArtifactFile);
        final Artifact mockArtifact = artifact.setFile(mockArtifactFile.toFile()).setVersion(PATCHED_ARTIFACT_VERSION);
        return mockArtifact;
    }

    private Optional<Artifact> readArtifactFromManifest(Path manifestFile, String groupId, String artifactId) throws IOException {
        return ManifestYamlSupport.parse(manifestFile.toFile()).getStreams().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }
}