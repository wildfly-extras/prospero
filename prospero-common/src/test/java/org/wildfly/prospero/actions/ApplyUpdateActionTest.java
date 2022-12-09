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

package org.wildfly.prospero.actions;

import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.creator.FeaturePackCreator;
import org.jboss.galleon.repo.RepositoryArtifactResolver;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.repo.SimplisticMavenRepoManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.api.FileConflict;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.installation.git.GitStorage;
import org.wildfly.prospero.utils.filestate.DirState;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ApplyUpdateActionTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private Path repoHome;
    private RepositoryArtifactResolver repo;
    private Path installationPath;
    private Path updatePath;
    private DirState.DirBuilder dirBuilder;
    private FeaturePackCreator creator;

    @Before
    public void setUp() throws Exception {
        repoHome = temp.newFolder().toPath();
        installationPath = temp.newFolder().toPath();
        updatePath = temp.newFolder().toPath();
        repo = initRepoManager(repoHome);
        creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        dirBuilder = DirState.rootBuilder()
                .skip(Constants.PROVISIONED_STATE_DIR)
                .skip(InstallationMetadata.METADATA_DIR);
    }

    @Test
    public void testUpdateNoUserChanges() throws Exception {
        final String fpl100 = "org.test:pack-one:1.0.0:zip";
        final String fpl101 = "org.test:pack-one:1.0.1:zip";

        final DirState expectedState = dirBuilder
                .addFile("prod1/p1.txt", "p1 1.0.1")
                .build();

        // build test packages
        creator.newFeaturePack(FeaturePackLocation.fromString(fpl100).getFPID())
                .newPackage("p1", true)
                .writeContent("prod1/p1.txt", "p1 1.0.0")
                .getFeaturePack();
        creator.newFeaturePack(FeaturePackLocation.fromString(fpl101).getFPID())
                .newPackage("p1", true)
                .writeContent("prod1/p1.txt", "p1 1.0.1")
                .getFeaturePack();
        creator.install();

        // install base and update. perform apply-update
        install(installationPath, fpl100);
        install(updatePath, fpl101);
        final List<FileConflict> conflicts = new ApplyUpdateAction(installationPath, updatePath).applyUpdate();

        // verify
        expectedState.assertState(installationPath);
        assertThat(conflicts).isEmpty();
    }

    @Test
    public void testUpdateWithUserChanges() throws Exception {
        final String fpl100 = "org.test:pack-one:1.0.0:zip";
        final String fpl101 = "org.test:pack-one:1.0.1:zip";

        final DirState expectedState = dirBuilder
                .addFile("prod1/p1.txt", "user prod1/p1")
                .addFile("prod1/p1.txt.glnew", "prod1/p1 1.0.1")
                .addFile("prod1/p2.txt", "prod1/p2 1.0.1")
                .addFile("prod1/p4.txt", "user prod1/p4")
                .addFile("prod1/p4.txt.glnew", "prod1/p4 1.0.1")
                .addFile("prod2/p2.txt", "user prod2/p2")
                .addFile("prod3/p1.txt", "prod3/p1 1.0.1")
                .addFile("prod4/p1.txt", "user prod4/p1")
                .addFile("prod4/p1.txt.glnew", "prod4/p1 1.0.1")
                .addFile("new_dir/users.txt", "user new_dir/users.txt")
                .addFile("new.file", "user new file")
                .build();

        // build test packages
        creator.newFeaturePack(FeaturePackLocation.fromString(fpl100).getFPID())
                .newPackage("p1", true)
                .writeContent("prod1/p1.txt", "prod1/p1 1.0.0")
                .writeContent("prod1/p2.txt", "prod1/p2 1.0.0") // removed by user, restored in update
                .writeContent("prod1/p3.txt", "prod1/p3 1.0.0") // removed by user, not restored in update
                .writeContent("prod2/p1.txt", "prod2/p1 1.0.0") // removed by update
                .writeContent("prod2/p2.txt", "prod2/p2 1.0.0") // removed by update, updated by user
                .getFeaturePack();
        creator.newFeaturePack(FeaturePackLocation.fromString(fpl101).getFPID())
                .newPackage("p1", true)
                .writeContent("prod1/p1.txt", "prod1/p1 1.0.1")
                .writeContent("prod1/p2.txt", "prod1/p2 1.0.1")
                .writeContent("prod1/p4.txt", "prod1/p4 1.0.1") // not present in base, added by user and overwritten
                .writeContent("prod3/p1.txt", "prod3/p1 1.0.1") // not present in base, not added by user
                .writeContent("prod4/p1.txt", "prod4/p1 1.0.1") // not present in base, added by user in new directory
                .getFeaturePack();
        creator.install();

        // install base
        install(installationPath, fpl100);
        // perform user changes
        writeContent("prod1/p1.txt", "user prod1/p1");
        writeContent("new.file", "user new file");
        Files.delete(installationPath.resolve("prod1/p2.txt"));
        writeContent("prod1/p4.txt", "user prod1/p4");
        Files.createDirectory(installationPath.resolve("new_dir"));
        Files.createDirectory(installationPath.resolve("prod4"));
        writeContent("prod4/p1.txt", "user prod4/p1");
        writeContent("prod2/p2.txt", "user prod2/p2");
        writeContent("new_dir/users.txt", "user new_dir/users.txt");
        // update
        install(updatePath, fpl101);
        final List<FileConflict> conflicts = new ApplyUpdateAction(installationPath, updatePath).applyUpdate();

        // verify
        expectedState.assertState(installationPath);
        assertThat(conflicts).containsExactlyInAnyOrder(
                FileConflict.userModified("prod1/p1.txt").updateModified().userPreserved(),
                FileConflict.userAdded("prod1/p4.txt").updateAdded().userPreserved(),
                FileConflict.userAdded("prod4/p1.txt").updateAdded().userPreserved(),
                FileConflict.userModified("prod2/p2.txt").updateRemoved().userPreserved()
        );
    }


    @Test
    public void testUserChangesInSystemPaths() throws Exception {
        final String fpl100 = "org.test:pack-one:1.0.0:zip";
        final String fpl101 = "org.test:pack-one:1.0.1:zip";

        final DirState expectedState = dirBuilder
                .addFile("prod1/p1.txt", "p1 1.0.1")
                .addFile("prod1/p1.txt.glold", "user prod1/p1")
                .addFile("prod1/p2.txt", "p2 1.0.1")
                .addFile("prod1/p3.txt", "p3 1.0.1")
                .addFile("prod1/p3.txt.glold", "user prod1/p3")
                .build();

        // build test packages
        creator.newFeaturePack(FeaturePackLocation.fromString(fpl100).getFPID())
                .addSystemPaths("prod1")
                .newPackage("p1", true)
                .writeContent("prod1/p1.txt", "p1 1.0.0") // modified by user and updated in update
                .writeContent("prod1/p2.txt", "p2 1.0.0") // removed by user and restored in update
                .getFeaturePack();
        creator.newFeaturePack(FeaturePackLocation.fromString(fpl101).getFPID())
                .addSystemPaths("prod1")
                .newPackage("p1", true)
                .writeContent("prod1/p1.txt", "p1 1.0.1")
                .writeContent("prod1/p2.txt", "p2 1.0.1")
                .writeContent("prod1/p3.txt", "p3 1.0.1") // created by user, added in update
                .getFeaturePack();
        creator.install();

        // install base and update. perform apply-update
        install(installationPath, fpl100);
        writeContent("prod1/p1.txt", "user prod1/p1");
        Files.delete(installationPath.resolve("prod1/p2.txt"));
        writeContent("prod1/p3.txt", "user prod1/p3");
        install(updatePath, fpl101);
        final List<FileConflict> conflicts = new ApplyUpdateAction(installationPath, updatePath).applyUpdate();

        // verify
        expectedState.assertState(installationPath);
        FileConflict.userAdded("prod1/p3.txt").updateAdded().userPreserved();
        assertThat(conflicts).containsExactlyInAnyOrder(
                FileConflict.userModified("prod1/p1.txt").updateModified().overwritten(),
                FileConflict.userRemoved("prod1/p2.txt").updateModified().overwritten(),
                FileConflict.userAdded("prod1/p3.txt").updateAdded().overwritten()
        );
    }

    @Test
    public void testMetadataUpdated() throws Exception {
        // 1. manifest.yaml is updated
        // 2. installation-channels.yaml are not modified
        // 3. .galleon is updated with new values

        final String fpl100 = "org.test:pack-one:1.0.0:zip";
        final String fpl101 = "org.test:pack-one:1.0.1:zip";

        final DirState expectedState = DirState.rootBuilder()
                .skip("prod1")
                .skip(InstallationMetadata.METADATA_DIR + "/" + ".git")
                .skip(Constants.PROVISIONED_STATE_DIR)
                .addFile(InstallationMetadata.METADATA_DIR + "/" + InstallationMetadata.MANIFEST_FILE_NAME, "manifest " + fpl101)
                .addFile(InstallationMetadata.METADATA_DIR + "/" + InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME, "channels " + fpl100)
                .build();

        // build test packages
        creator.newFeaturePack(FeaturePackLocation.fromString(fpl100).getFPID())
                .newPackage("p1", true)
                .writeContent("prod1/p1.txt", "p1 1.0.0")
                .getFeaturePack();
        creator.newFeaturePack(FeaturePackLocation.fromString(fpl101).getFPID())
                .newPackage("p1", true)
                .writeContent("prod1/p1.txt", "p1 1.0.1")
                .getFeaturePack();
        creator.install();

        // install base and update. perform apply-update
        install(installationPath, fpl100);
        install(updatePath, fpl101);
        new ApplyUpdateAction(installationPath, updatePath).applyUpdate();

        // verify
        expectedState.assertState(installationPath);
        assertTrue(Files.readString(installationPath.resolve(Constants.PROVISIONED_STATE_DIR).resolve(Constants.PROVISIONING_XML))
                .contains(fpl101));
        assertTrue(Files.readString(installationPath.resolve(Constants.PROVISIONED_STATE_DIR).resolve(Constants.PROVISIONED_STATE_XML))
                .contains(fpl101));
        // verify update was recorded
        assertEquals(2, new GitStorage(installationPath).getRevisions().size());
    }

    private void writeContent(String path, String content) throws IOException {
        Files.writeString(installationPath.resolve(path.replace("/", File.separator)), content);
    }

    private void install(Path path, String fpl) throws Exception {
        HashMap<String, String> options = new HashMap<>();
        options.put(Constants.EXPORT_SYSTEM_PATHS, "true");
        getPm(path).install(FeaturePackLocation.fromString(fpl), options);
        // mock the installation metadata
        Files.createDirectory(path.resolve(InstallationMetadata.METADATA_DIR));
        Files.writeString(path.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.MANIFEST_FILE_NAME), "manifest " + fpl);
        Files.writeString(path.resolve(InstallationMetadata.METADATA_DIR).resolve(InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME), "channels " + fpl);
        new GitStorage(path).record();
    }

    protected RepositoryArtifactResolver initRepoManager(Path repoHome) {
        return SimplisticMavenRepoManager.getInstance(repoHome);
    }

    protected ProvisioningManager getPm(Path path) throws Exception {
        return ProvisioningManager.builder()
                .addArtifactResolver(repo)
                .setInstallationHome(path)
                .setRecordState(true)
                .build();
    }
}