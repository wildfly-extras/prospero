package org.wildfly.prospero.spi;


import org.jboss.galleon.ProvisioningException;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.HistoryRevisionResult;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ProsperoInstallationManager implements InstallationManager {

    private MavenSessionManager mavenSessionManager;
    private Path server;

    @Override
    public InstallationManager initialize(Path installationDir) throws ProvisioningException {
        this.server = installationDir;
        mavenSessionManager = new MavenSessionManager();
        return this;
    }

    @Override
    public String getName() {
        return "prospero";
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
    public List<HistoryRevisionResult> history(String revision) throws MetadataException {
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

    private void verifyInstallationDirectory(Path path) {
        File dotGalleonDir = path.resolve(InstallationMetadata.GALLEON_INSTALLATION_DIR).toFile();
        File prosperoConfigFile = path.resolve(InstallationMetadata.METADATA_DIR)
                .resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME).toFile();
        if (!dotGalleonDir.isDirectory() || !prosperoConfigFile.isFile()) {
            throw Messages.MESSAGES.invalidInstallationDir(path);
        }
    }
}
