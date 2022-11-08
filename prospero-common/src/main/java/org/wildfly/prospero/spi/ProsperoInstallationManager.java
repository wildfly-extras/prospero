package org.wildfly.prospero.spi;

import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
