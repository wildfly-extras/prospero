package org.wildfly.prospero.spi;

import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProsperoInstallationManager implements InstallationManager {

    private MavenSessionManager mavenSessionManager;
    private Path server;

    public ProsperoInstallationManager(Path installationDir, MavenOptions mavenOptions) throws Exception {
        this.server = installationDir;
        mavenSessionManager = new MavenSessionManager(
                Optional.of(mavenOptions.getLocalRepository()), mavenOptions.isOffline());
    }

    @Override
    public List<HistoryResult> history() throws Exception {
        verifyInstallationDirectory(this.server);
        final InstallationHistoryAction historyAction = new InstallationHistoryAction(this.server, null);
        final List<SavedState> revisions = historyAction.getRevisions();
        final List<HistoryResult> results = new ArrayList<>();

        for (SavedState savedState : revisions) {
            results.add(new HistoryResult(savedState.getName(), savedState.getTimestamp(), savedState.getType().toString()));
        }
        return results;
    }

    @Override
    public List<ArtifactChange> revisionDetails(String revision) throws MetadataException {
        Objects.requireNonNull(revision);
        final InstallationHistoryAction historyAction = new InstallationHistoryAction(this.server, null);
        final List<org.wildfly.prospero.api.ArtifactChange> changes = historyAction.compare(new SavedState(revision));

        if (changes.isEmpty()) {
            return Collections.EMPTY_LIST;
        } else {
            return changes.stream().map(ProsperoInstallationManager::mapChange).collect(Collectors.toList());
        }
    }

    @Override
    public void update() throws Exception {
        try (final UpdateAction updateAction = new UpdateAction(server, mavenSessionManager, null, Collections.emptyList())) {
            updateAction.performUpdate();
        }
    }

    @Override
    public List<ArtifactChange> findUpdates() throws Exception {
        try (final UpdateAction updateAction = new UpdateAction(server, mavenSessionManager, null, Collections.emptyList())) {
            final UpdateSet updates = updateAction.findUpdates();
            return updates.getArtifactUpdates().stream()
                    .map(ProsperoInstallationManager::mapChange)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public Collection<Channel> listChannels() throws OperationException {
        try (final MetadataAction metadataAction = new MetadataAction(server)) {
            return metadataAction.getChannels().stream()
                    .map(ProsperoInstallationManager::mapChannel)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void removeChannel(Channel channel) throws OperationException {
        try (final MetadataAction metadataAction = new MetadataAction(server)) {
            final int index = map(metadataAction.getChannels(), ProsperoInstallationManager::mapChannel).indexOf(channel);
            if (index < 0) {
                throw new MetadataException("Required channel cannot be found.");
            }
            metadataAction.removeChannel(index);
        }
    }

    @Override
    public void addChannel(Channel channel) throws OperationException {
        try (final MetadataAction metadataAction = new MetadataAction(server)) {
            metadataAction.addChannel(mapChannel(channel));
        }
    }

    @Override
    public void changeChannel(Channel oldChannel, Channel newChannel) throws OperationException {
        try (final MetadataAction metadataAction = new MetadataAction(server)) {
            final int index = map(metadataAction.getChannels(), ProsperoInstallationManager::mapChannel).indexOf(oldChannel);
            if (index < 0) {
                throw new MetadataException("Required channel cannot be found.");
            }
            metadataAction.changeChannel(index, mapChannel(newChannel));
        }
    }

    private static Channel mapChannel(org.wildfly.channel.Channel channel) {
        if (channel.getManifestRef().getUrl() != null) {
            return new Channel(channel.getName(), map(channel.getRepositories(), ProsperoInstallationManager::mapRepository), channel.getManifestRef().getUrl());
        } else if (channel.getManifestRef().getGav() != null) {
            return new Channel(channel.getName(), map(channel.getRepositories(), ProsperoInstallationManager::mapRepository), channel.getManifestRef().getGav());
        } else {
            return new Channel(channel.getName(), map(channel.getRepositories(), ProsperoInstallationManager::mapRepository));
        }
    }

    private static org.wildfly.channel.Channel mapChannel(Channel channel) {
        return new org.wildfly.channel.Channel(channel.getName(), null, null, null,
                map(channel.getRepositories(), ProsperoInstallationManager::mapRepository), toManifestCoordinate(channel));
    }

    private static ChannelManifestCoordinate toManifestCoordinate(Channel c) {
        if (c.getManifestUrl().isPresent()) {
            return new ChannelManifestCoordinate(c.getManifestUrl().get());
        } else if (c.getManifestCoordinate().isPresent()) {
            final String[] coordinate = c.getManifestCoordinate().get().split(":");
            return new ChannelManifestCoordinate(coordinate[0], coordinate[1]);
        } else {
            return null;
        }
    }

    private static <T, R> List<R> map(List<T> subject, Function<T,R> mapper) {
        return subject.stream().map(mapper::apply).collect(Collectors.toList());
    }

    private static org.wildfly.channel.Repository mapRepository(Repository repository) {
        return new org.wildfly.channel.Repository(repository.getId(), repository.getUrl());
    }

    private static Repository mapRepository(org.wildfly.channel.Repository repository) {
        return new Repository(repository.getId(), repository.getUrl());
    }

    private static ArtifactChange mapChange(org.wildfly.prospero.api.ArtifactChange c) {
        if (c.isInstalled()) {
            return new ArtifactChange(null, c.getNewVersion().get(), c.getArtifactName(), ArtifactChange.Status.INSTALLED);
        } else if (c.isRemoved()) {
            return new ArtifactChange(c.getOldVersion().get(), null, c.getArtifactName(), ArtifactChange.Status.REMOVED);
        } else {
            return new ArtifactChange(c.getOldVersion().get(), c.getNewVersion().get(), c.getArtifactName(), ArtifactChange.Status.UPDATED);
        }
    }

    private void verifyInstallationDirectory(Path path) {
        File dotGalleonDir = path.resolve(InstallationMetadata.GALLEON_INSTALLATION_DIR).toFile();
        File prosperoConfigFile = path.resolve(InstallationMetadata.METADATA_DIR)
                .resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME).toFile();
        if (!dotGalleonDir.isDirectory() || !prosperoConfigFile.isFile()) {
            throw Messages.MESSAGES.invalidInstallationDir(path);
        }
    }
}
