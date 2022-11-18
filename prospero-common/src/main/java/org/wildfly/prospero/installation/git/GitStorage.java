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

package org.wildfly.prospero.installation.git;

import org.eclipse.jgit.lib.StoredConfig;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GitStorage implements AutoCloseable {

    public static final String GIT_HISTORY_USER = "Wildfly Installer";
    private final Git git;
    private Path base;

    public GitStorage(Path base) throws MetadataException {
        this.base = base.resolve(InstallationMetadata.METADATA_DIR);
        try {
            git = initGit();
        } catch (GitAPIException | IOException e) {
            throw Messages.MESSAGES.unableToCreateHistoryStorage(base, e);
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
            throw Messages.MESSAGES.unableToAccessHistoryStorage(base, e);
        }
    }

    public void record() throws MetadataException {
        try {
            git.add().addFilepattern(InstallationMetadata.MANIFEST_FILE_NAME).call();

            if (isRepositoryEmpty(git)) {
                git.add().addFilepattern(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME).call();
                // adjust the date so that when talking over a non-prosper installation date matches creation
                git.commit()
                        .setCommitter(adjustCommitDateToCreationDate(getCommitter()))
                        .setMessage(SavedState.Type.INSTALL.name())
                        .call();
            } else {
                git.commit().setCommitter(getCommitter()).setMessage(SavedState.Type.UPDATE.name()).call();
            }

        } catch (IOException | GitAPIException e) {
            throw Messages.MESSAGES.unableToAccessHistoryStorage(base, e);
        }
    }

    /*
     * The PersonIdent needs to be created on commit to capture current time
     */
    private PersonIdent getCommitter() {
        return new PersonIdent(GIT_HISTORY_USER, "");
    }

    private PersonIdent adjustCommitDateToCreationDate(PersonIdent committer) throws IOException {
        final FileTime fileTime = Files.readAttributes(base, BasicFileAttributes.class).creationTime();
        return new PersonIdent(committer, fileTime.toMillis(), committer.getTimeZone().getRawOffset());
    }

    public void recordConfigChange() throws MetadataException {
        try {
            git.add().addFilepattern(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME).call();
            git.commit().setCommitter(getCommitter()).setMessage(SavedState.Type.CONFIG_CHANGE.name()).call();
        } catch (GitAPIException e) {
            throw Messages.MESSAGES.unableToAccessHistoryStorage(base, e);
        }
    }

    public void revert(SavedState savedState) throws MetadataException {
        try {
            git.checkout()
                    .setStartPoint(savedState.getName())
                    .addPath(InstallationMetadata.MANIFEST_FILE_NAME)
                    .call();
            git.add().addFilepattern(InstallationMetadata.MANIFEST_FILE_NAME).call();
            git.commit().setCommitter(getCommitter()).setMessage(SavedState.Type.ROLLBACK.name()).call();
        } catch (GitAPIException e) {
            throw Messages.MESSAGES.unableToAccessHistoryStorage(base, e);
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
            final ChannelManifest parseOld = ManifestYamlSupport.parse(hist.resolve(InstallationMetadata.MANIFEST_FILE_NAME).toFile());
            final ChannelManifest parseCurrent = ManifestYamlSupport.parse(base.resolve(InstallationMetadata.MANIFEST_FILE_NAME).toFile());

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
            final StoredConfig config = git.getRepository().getConfig();
            config.setBoolean("commit", null, "gpgsign", false);
            config.save();
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

    public boolean isStarted() throws IOException {
        return !isRepositoryEmpty(git);
    }
}
