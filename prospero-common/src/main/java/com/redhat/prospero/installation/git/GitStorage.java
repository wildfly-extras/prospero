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

package com.redhat.prospero.installation.git;

import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.api.SavedState;
import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.model.ManifestYamlSupport;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GitStorage implements AutoCloseable {

    public static final String GIT_HISTORY_USER = "EAP Installer";
    public static final PersonIdent GIT_HISTORY_COMMITTER = new PersonIdent(GIT_HISTORY_USER, "");
    private final Git git;
    private Path base;

    public GitStorage(Path base) throws MetadataException {
        this.base = base.resolve(InstallationMetadata.METADATA_DIR);
        try {
            git = initGit();
        } catch (GitAPIException | IOException e) {
            throw new MetadataException("Unable to open or create git repository for the installation", e);
        }
    }

    public List<SavedState> getRevisions() throws MetadataException {
        try {
            final Iterable<RevCommit> call = git.log().call();
            List<SavedState> history = new ArrayList<>();
            for (RevCommit revCommit : call) {
                history.add(new SavedState(revCommit.getName().substring(0,8),
                        Instant.ofEpochSecond(revCommit.getCommitTime()),
                        SavedState.Type.valueOf(revCommit.getShortMessage().toUpperCase(Locale.ROOT))));
            }

            return history;
        } catch (GitAPIException e) {
            throw new MetadataException("Unable to read history of installation", e);
        }
    }

    public void record() throws MetadataException {
        try {
            git.add().addFilepattern(InstallationMetadata.MANIFEST_FILE_NAME).call();

            if (isRepositoryEmpty(git)) {
                git.commit().setCommitter(GIT_HISTORY_COMMITTER).setMessage(SavedState.Type.INSTALL.name()).call();
            } else {
                git.commit().setCommitter(GIT_HISTORY_COMMITTER).setMessage(SavedState.Type.UPDATE.name()).call();
            }

        } catch (IOException | GitAPIException e) {
            throw new MetadataException("Unable to write history of installation", e);
        }
    }

    public void revert(SavedState savedState) throws MetadataException {
        try {
            git.checkout()
                    .setStartPoint(savedState.getName())
                    .addPath(InstallationMetadata.MANIFEST_FILE_NAME)
                    .call();
            git.add().addFilepattern(InstallationMetadata.MANIFEST_FILE_NAME).call();
            git.commit().setCommitter(GIT_HISTORY_COMMITTER).setMessage(SavedState.Type.ROLLBACK.name()).call();
        } catch (GitAPIException e) {
            throw new MetadataException("Unable to write history of installation", e);
        }
    }

    public List<ArtifactChange> getChanges(SavedState savedState) throws MetadataException {
        Path hist = null;
        Git temp = null;
        try {
            hist = Files.createTempDirectory("hist");
            temp = Git.cloneRepository()
                    .setDirectory(hist.toFile())
                    .setRemote("origin")
                    .setURI(base.toUri().toString())
                    .call();
            temp.checkout()
                    .setStartPoint(savedState.getName())
                    .addPath(InstallationMetadata.MANIFEST_FILE_NAME)
                    .call();
            final Channel parseOld = ManifestYamlSupport.parse(hist.resolve(InstallationMetadata.MANIFEST_FILE_NAME).toFile());
            final Channel parseCurrent = ManifestYamlSupport.parse(base.resolve(InstallationMetadata.MANIFEST_FILE_NAME).toFile());

            final Map<String, Artifact> oldArtifacts = toMap(parseOld.getStreams());
            final Map<String, Artifact> currentArtifacts = toMap(parseCurrent.getStreams());

            final ArrayList<ArtifactChange> artifactChanges = new ArrayList<>();
            for (String ga : currentArtifacts.keySet()) {
                if (!oldArtifacts.containsKey(ga)) {
                    artifactChanges.add(new ArtifactChange(null, currentArtifacts.get(ga)));
                } else if (!currentArtifacts.get(ga).getVersion().equals(oldArtifacts.get(ga).getVersion())) {
                    artifactChanges.add(new ArtifactChange(oldArtifacts.get(ga), currentArtifacts.get(ga)));
                }
            }
            for (String ga: oldArtifacts.keySet()) {
                if (!currentArtifacts.containsKey(ga)) {
                    artifactChanges.add(new ArtifactChange(oldArtifacts.get(ga), null));
                }
            }

            return artifactChanges;
        } catch (GitAPIException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (temp != null) {
                    temp.close();
                }
                if (hist != null) {
                    FileUtils.deleteDirectory(hist.toFile());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return Collections.emptyList();
    }

    private Map<String, Artifact> toMap(Collection<Stream> artifacts) {
        final HashMap<String, Artifact> map = new HashMap<>();
        for (Stream stream : artifacts) {
            DefaultArtifact artifact = new DefaultArtifact(stream.getGroupId(), stream.getArtifactId(), "jar", stream.getVersion());
            map.put(stream.getGroupId() + ":" + stream.getArtifactId(), artifact);
        }
        return map;
    }

    private boolean isRepositoryEmpty(Git git) throws IOException {
        return git.getRepository().resolve(Constants.HEAD) == null;
    }

    private Git initGit() throws GitAPIException, IOException {
        Git git;
        if (!base.resolve(".git").toFile().exists()) {
            git = Git.init().setDirectory(base.toFile()).call();
        } else {
            git = Git.open(base.toFile());
        }
        return git;
    }

    @Override
    public void close() throws Exception {
        if (git != null) {
            git.close();
        }
    }
}
