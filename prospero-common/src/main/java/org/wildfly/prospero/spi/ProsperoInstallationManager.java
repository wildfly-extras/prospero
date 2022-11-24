package org.wildfly.prospero.spi;

import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.InstallationChanges;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.actions.InstallationExportAction;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
                Optional.ofNullable(mavenOptions.getLocalRepository()), mavenOptions.isOffline());
    }

    @Override
    public List<HistoryResult> history() throws Exception {
        final InstallationHistoryAction historyAction = new InstallationHistoryAction(this.server, null);
        final List<SavedState> revisions = historyAction.getRevisions();
        final List<HistoryResult> results = new ArrayList<>();

        for (SavedState savedState : revisions) {
            results.add(new HistoryResult(savedState.getName(), savedState.getTimestamp(), savedState.getType().toString()));
        }
        return results;
    }

    @Override
    public InstallationChanges revisionDetails(String revision) throws MetadataException {
        Objects.requireNonNull(revision);
        final InstallationHistoryAction historyAction = new InstallationHistoryAction(this.server, null);
        final List<org.wildfly.prospero.api.ArtifactChange> changes = historyAction.compare(new SavedState(revision));

        if (changes.isEmpty()) {
            return new InstallationChanges(Collections.EMPTY_LIST, Collections.EMPTY_LIST);
        } else {
            return new InstallationChanges(
                    changes.stream().map(ProsperoInstallationManager::mapChange).collect(Collectors.toList()),
                    Collections.EMPTY_LIST);
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
    public void removeChannel(String channelName) throws OperationException {
        try (final MetadataAction metadataAction = new MetadataAction(server)) {
            metadataAction.removeChannel(channelName);
        }
    }

    @Override
    public void addChannel(Channel channel) throws OperationException {
        try (final MetadataAction metadataAction = new MetadataAction(server)) {
            metadataAction.addChannel(mapChannel(channel));
        }
    }

    @Override
    public void changeChannel(String channelName, Channel newChannel) throws OperationException {
        try (final MetadataAction metadataAction = new MetadataAction(server)) {
            metadataAction.changeChannel(channelName, mapChannel(newChannel));
        }
    }

    @Override
    public Path createSnapshot(Path targetPath) throws Exception {
        final Path snapshotPath;
        if (!Files.exists(targetPath)) {
            if (targetPath.endsWith(".zip")) {
                snapshotPath = targetPath.toAbsolutePath();
            } else {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                snapshotPath = targetPath.resolve("im-snapshot-" + timestamp + ".zip").toAbsolutePath();
            }
        } else if (Files.isDirectory(targetPath)) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            snapshotPath = targetPath.resolve("im-snapshot-" + timestamp + ".zip").toAbsolutePath();
        } else {
            throw Messages.MESSAGES.fileAlreadyExists(targetPath);
        }

        final InstallationExportAction installationExportAction = new InstallationExportAction(server);
        installationExportAction.export(snapshotPath.toString());

        return snapshotPath;
    }

    private static Channel mapChannel(org.wildfly.channel.Channel channel) {
        if (channel.getManifestRef() == null) {
            return new Channel(channel.getName(), map(channel.getRepositories(), ProsperoInstallationManager::mapRepository));
        } else if (channel.getManifestRef().getUrl() != null) {
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
}
