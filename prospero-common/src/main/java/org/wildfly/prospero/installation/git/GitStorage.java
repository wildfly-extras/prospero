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

import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.StoredConfig;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.ChannelChange;
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
import org.wildfly.prospero.model.ProsperoConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
            if (isRepositoryEmpty(git)) {
                final PersonIdent author = adjustCommitDateToCreationDate(getCommitter());
                final SavedState.Type commitType = SavedState.Type.INSTALL;
                git.add().addFilepattern(InstallationMetadata.MANIFEST_FILE_NAME).call();
                git.add().addFilepattern(InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME).call();
                // adjust the date so that when taking over a non-prosper installation date matches creation
                git.commit()
                        .setAuthor(author)
                        .setCommitter(author)
                        .setMessage(commitType.name())
                        .call();
            } else {
                recordChange(SavedState.Type.UPDATE);
            }
        } catch (IOException | GitAPIException e) {
            throw Messages.MESSAGES.unableToAccessHistoryStorage(base, e);
        }


    }

    public void recordChange(SavedState.Type operation) throws MetadataException {
        try {
            if (isRepositoryEmpty(git)) {
                throw new IllegalStateException("This operation cannot be performed on empty repository");
            }

            git.add().addFilepattern(InstallationMetadata.MANIFEST_FILE_NAME).call();

            final PersonIdent author = getCommitter();
            final SavedState.Type commitType = operation;

            git.commit()
                    .setAuthor(author)
                    .setCommitter(author)
                    .setMessage(commitType.name())
                    .call();

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
            git.add().addFilepattern(InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME).call();
            final PersonIdent author = getCommitter();
            git.commit()
                    .setAuthor(author)
                    .setCommitter(author)
                    .setMessage(SavedState.Type.CONFIG_CHANGE.name())
                    .call();
        } catch (GitAPIException e) {
            throw Messages.MESSAGES.unableToAccessHistoryStorage(base, e);
        }
    }

    public void revert(SavedState savedState) throws MetadataException {
        try {
            git.checkout()
                    .setStartPoint(savedState.getName())
                    .setAllPaths(true)
                    .call();
        } catch (GitAPIException e) {
            throw Messages.MESSAGES.unableToAccessHistoryStorage(base, e);
        }
    }

    public void reset() throws MetadataException {
        try {
            git.reset()
                    .setRef("HEAD")
                    .setMode(ResetCommand.ResetType.HARD)
                    .call();
        } catch (GitAPIException e) {
            throw Messages.MESSAGES.unableToAccessHistoryStorage(base, e);
        }
    }

    public List<ArtifactChange> getArtifactChanges(SavedState savedState) throws MetadataException {
        Parser<ArtifactChange> parser = (path) -> {
            final ChannelManifest parseOld = ManifestYamlSupport.parse(path.resolve(InstallationMetadata.MANIFEST_FILE_NAME).toFile());
            final ChannelManifest parseCurrent = ManifestYamlSupport.parse(base.resolve(InstallationMetadata.MANIFEST_FILE_NAME).toFile());

            final Map<String, Artifact> oldArtifacts = toMap(parseOld.getStreams());
            final Map<String, Artifact> currentArtifacts = toMap(parseCurrent.getStreams());

            final ArrayList<ArtifactChange> artifactChanges = new ArrayList<>();
            for (String ga : currentArtifacts.keySet()) {
                if (!oldArtifacts.containsKey(ga)) {
                    artifactChanges.add(ArtifactChange.added(currentArtifacts.get(ga)));
                } else if (!currentArtifacts.get(ga).getVersion().equals(oldArtifacts.get(ga).getVersion())) {
                    artifactChanges.add(ArtifactChange.updated(oldArtifacts.get(ga), currentArtifacts.get(ga)));
                }
            }
            for (String ga : oldArtifacts.keySet()) {
                if (!currentArtifacts.containsKey(ga)) {
                    artifactChanges.add(ArtifactChange.removed(oldArtifacts.get(ga)));
                }
            }

            return artifactChanges;
        };

        return getChanges(savedState, InstallationMetadata.MANIFEST_FILE_NAME, parser);
    }

    public List<ChannelChange> getChannelChanges(SavedState savedState) throws MetadataException {
        Parser<ChannelChange> parser = (path) -> {

            final List<Channel> oldChannels = ProsperoConfig.readConfig(path.resolve(InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME)).getChannels();
            final List<Channel> currentChannels = ProsperoConfig.readConfig(base.resolve(InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME)).getChannels();

            final ArrayList<ChannelChange> channelChanges = new ArrayList<>();

            for (Channel current : currentChannels) {
                final Optional<Channel> oldChannel = oldChannels.stream()
                        .filter(old -> current.getName().equals(old.getName()))
                        .findFirst();
                if (oldChannel.isEmpty()) {
                    channelChanges.add(ChannelChange.added(current));
                } else {
                    final ChannelChange change = ChannelChange.modified(oldChannel.get(), current);
                    if (!change.getChildren().isEmpty()) {
                        channelChanges.add(change);
                    }
                }
            }

            for (Channel old : oldChannels) {
                final Optional<Channel> currentChannel = currentChannels.stream()
                        .filter(current -> current.getName().equals(old.getName()))
                        .findFirst();
                if (currentChannel.isEmpty()) {
                    channelChanges.add(ChannelChange.removed(old));
                }
            }

            return channelChanges;
        };

        return getChanges(savedState, InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME, parser);
    }

    public <T> List<T> getChanges(SavedState savedState, String manifestFileName, Parser<T> parser) throws MetadataException {
        Path hist = null;
        try {
            hist = checkoutPastState(savedState, manifestFileName);

            return parser.parse(hist);
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw Messages.MESSAGES.unableToParseConfiguration(hist, e);
        } finally {
            try {
                if (hist != null) {
                    FileUtils.deleteDirectory(hist.toFile());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
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
            config.setString("user", null, "name", GIT_HISTORY_USER);
            config.setString("user", null, "email", "");
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

    private Path checkoutPastState(SavedState savedState, String fileName) throws GitAPIException, IOException {
        Path hist = Files.createTempDirectory("hist");
        try (Git temp =  Git.cloneRepository()
                    .setDirectory(hist.toFile())
                    .setRemote("origin")
                    .setURI(base.toUri().toString())
                    .call()) {
            temp.checkout()
                    .setStartPoint(savedState.getName())
                    .addPath(fileName)
                    .call();
            return hist;
        }
    }

    private interface Parser<T> {
        List<T> parse(Path checkoutPath) throws IOException, MetadataException;
    }
}
