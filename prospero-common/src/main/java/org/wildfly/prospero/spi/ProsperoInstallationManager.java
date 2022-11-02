package org.wildfly.prospero.spi;

import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.HistoryRevisionResult;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.ArtifactChange;
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
    public List<HistoryRevisionResult> revisionDetails(String revision) throws MetadataException {
        Objects.requireNonNull(revision);
        final InstallationHistoryAction historyAction = new InstallationHistoryAction(this.server, null);
        final List<ArtifactChange> changes = historyAction.compare(new SavedState(revision));

        if (changes.isEmpty()) {
            return Collections.EMPTY_LIST;
        } else {
            return changes.stream().map(c -> {
                if (c.isInstalled()) {
                    return new HistoryRevisionResult(null, c.getNewVersion().get(), null, c.getNewGav().get(), HistoryRevisionResult.Status.INSTALLED);
                } else if (c.isRemoved()) {
                    return new HistoryRevisionResult(c.getOldVersion().get(), null, c.getOldGav().get(), null, HistoryRevisionResult.Status.REMOVED);
                } else {
                    return new HistoryRevisionResult(c.getOldVersion().get(), c.getNewVersion().get(), c.getOldGav().get(), c.getNewGav().get(), HistoryRevisionResult.Status.UPDATED);
                }
            }).collect(Collectors.toList());
        }
    }

    public void update() throws Exception {
        try (final UpdateAction updateAction = new UpdateAction(server, mavenSessionManager, null, Collections.emptyList())) {
            updateAction.performUpdate();
        }
    }

    public UpdateSet findUpdates() throws Exception {
        try (final UpdateAction updateAction = new UpdateAction(server, mavenSessionManager, null, Collections.emptyList())) {
            return updateAction.findUpdates();
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
