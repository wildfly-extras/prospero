package org.wildfly.prospero.spi;

import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.ChannelChange;
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

    private final ActionFactory actionFactory;

    public ProsperoInstallationManager(Path installationDir, MavenOptions mavenOptions) throws Exception {
        MavenSessionManager mavenSessionManager = new MavenSessionManager(
                Optional.ofNullable(mavenOptions.getLocalRepository()), mavenOptions.isOffline());
        actionFactory = new ActionFactory(installationDir, mavenSessionManager);
    }

    // Used for tests to mock up action creation
    protected ProsperoInstallationManager(ActionFactory actionFactory) {
        this.actionFactory = actionFactory;
    }

    @Override
    public List<HistoryResult> history() throws Exception {
        final InstallationHistoryAction historyAction = actionFactory.getHistoryAction();
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
        final InstallationHistoryAction historyAction = actionFactory.getHistoryAction();
        final org.wildfly.prospero.api.InstallationChanges changes = historyAction.compare(new SavedState(revision));

        if (changes.isEmpty()) {
            return new InstallationChanges(Collections.EMPTY_LIST, Collections.EMPTY_LIST);
        } else {
            final List<ArtifactChange> artifacts = changes.getArtifactChanges().stream()
                    .map(ProsperoInstallationManager::mapArtifactChange)
                    .collect(Collectors.toList());

            final List<ChannelChange> channels = changes.getChannelChanges().stream()
                    .map(ProsperoInstallationManager::mapChannelChange)
                    .collect(Collectors.toList());
            return new InstallationChanges(artifacts, channels);
        }
    }

    @Override
    public void prepareUpdate(Path targetDir, List<Repository> repositories) throws Exception {
        try (final UpdateAction prepareUpdateAction = actionFactory.getUpdateAction(map(repositories, ProsperoInstallationManager::mapRepository))) {
            prepareUpdateAction.buildUpdate(targetDir);
        }
    }

    @Override
    public List<ArtifactChange> findUpdates(List<Repository> repositories) throws Exception {
        try (final UpdateAction updateAction = actionFactory.getUpdateAction(map(repositories, ProsperoInstallationManager::mapRepository))) {
            final UpdateSet updates = updateAction.findUpdates();
            return updates.getArtifactUpdates().stream()
                    .map(ProsperoInstallationManager::mapArtifactChange)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public Collection<Channel> listChannels() throws OperationException {
        try (final MetadataAction metadataAction = actionFactory.getMetadataAction()) {
            return metadataAction.getChannels().stream()
                    .map(ProsperoInstallationManager::mapChannel)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void removeChannel(String channelName) throws OperationException {
        try (final MetadataAction metadataAction = actionFactory.getMetadataAction()) {
            metadataAction.removeChannel(channelName);
        }
    }

    @Override
    public void addChannel(Channel channel) throws OperationException {
        try (final MetadataAction metadataAction = actionFactory.getMetadataAction()) {
            metadataAction.addChannel(mapChannel(channel));
        }
    }

    @Override
    public void changeChannel(String channelName, Channel newChannel) throws OperationException {
        try (final MetadataAction metadataAction = actionFactory.getMetadataAction()) {
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

        final InstallationExportAction installationExportAction = actionFactory.getInstallationExportAction();
        installationExportAction.export(snapshotPath);

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
        if (subject == null) {
            return Collections.emptyList();
        }
        return subject.stream().map(mapper::apply).collect(Collectors.toList());
    }

    private static org.wildfly.channel.Repository mapRepository(Repository repository) {
        return new org.wildfly.channel.Repository(repository.getId(), repository.getUrl());
    }

    private static Repository mapRepository(org.wildfly.channel.Repository repository) {
        return new Repository(repository.getId(), repository.getUrl());
    }

    private static ArtifactChange mapArtifactChange(org.wildfly.prospero.api.ArtifactChange change) {
        if (change.isInstalled()) {
            return new ArtifactChange(null, change.getNewVersion().get(), change.getArtifactName(), ArtifactChange.Status.INSTALLED);
        } else if (change.isRemoved()) {
            return new ArtifactChange(change.getOldVersion().get(), null, change.getArtifactName(), ArtifactChange.Status.REMOVED);
        } else {
            return new ArtifactChange(change.getOldVersion().get(), change.getNewVersion().get(), change.getArtifactName(), ArtifactChange.Status.UPDATED);
        }
    }

    private static ChannelChange mapChannelChange(org.wildfly.prospero.api.ChannelChange change) {
        final Channel oldChannel = change.getOldChannel() == null ? null : mapChannel(change.getOldChannel());
        final Channel newChannel = change.getNewChannel() == null ? null : mapChannel(change.getNewChannel());

        return new ChannelChange(oldChannel, newChannel);
    }

    protected static class ActionFactory {

        private final Path server;
        private final MavenSessionManager mavenSessionManager;

        private ActionFactory(Path server, MavenSessionManager mavenSessionManager) {
            this.server = server;
            this.mavenSessionManager = mavenSessionManager;
        }

        protected InstallationHistoryAction getHistoryAction() {
            return new InstallationHistoryAction(server, null);
        }

        protected UpdateAction getUpdateAction(List<org.wildfly.channel.Repository> repositories) throws OperationException {
            return new UpdateAction(server, mavenSessionManager, null, repositories);
        }

        protected MetadataAction getMetadataAction() throws MetadataException {
            return new MetadataAction(server);
        }

        protected InstallationExportAction getInstallationExportAction() {
            return new InstallationExportAction(server);
        }
    }
}
