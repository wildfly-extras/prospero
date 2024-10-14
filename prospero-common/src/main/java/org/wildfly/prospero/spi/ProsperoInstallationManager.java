package org.wildfly.prospero.spi;

import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.Logger;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.installationmanager.ArtifactChange;
import org.wildfly.installationmanager.CandidateType;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.ChannelChange;
import org.wildfly.installationmanager.FileConflict;
import org.wildfly.installationmanager.HistoryResult;
import org.wildfly.installationmanager.InstallationChanges;
import org.wildfly.installationmanager.ManifestVersion;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.MissingSignatureException;
import org.wildfly.installationmanager.OperationNotAvailableException;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.TrustCertificate;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.installationmanager.spi.OsShell;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.actions.ApplyCandidateAction;
import org.wildfly.prospero.actions.CertificateAction;
import org.wildfly.prospero.actions.InstallationExportAction;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.MavenOptions.Builder;
import org.wildfly.prospero.api.exceptions.InvalidUpdateCandidateException;
import org.wildfly.prospero.galleon.GalleonCallbackAdapter;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.signatures.PGPKeyId;
import org.wildfly.prospero.signatures.PGPPublicKey;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import org.wildfly.prospero.spi.internal.CliProvider;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.updates.UpdateSet;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProsperoInstallationManager implements InstallationManager {

    private static final Logger logger = Logger.getLogger(GalleonCallbackAdapter.class);

    private final ActionFactory actionFactory;
    private Path installationDir;
    private MavenOptions mavenOptions;

    public ProsperoInstallationManager(Path installationDir, MavenOptions mavenOptions) throws Exception {
        final Builder options = org.wildfly.prospero.api.MavenOptions.builder()
                .setOffline(mavenOptions.isOffline());
        if (mavenOptions.getLocalRepository() != null) {
            options.setNoLocalCache(false);
            options.setLocalCachePath(mavenOptions.getLocalRepository());
        }
        actionFactory = new ActionFactory(installationDir, options.build());
        this.installationDir = installationDir;
        this.mavenOptions = mavenOptions;
    }

    // Used for tests to mock up action creation
    protected ProsperoInstallationManager(ActionFactory actionFactory) {
        this.actionFactory = actionFactory;
    }

    @Override
    public List<HistoryResult> history() throws Exception {
        logger.info("Listing installation history");
        final InstallationHistoryAction historyAction = actionFactory.getHistoryAction();
        final List<SavedState> revisions = historyAction.getRevisions();
        final List<HistoryResult> results = new ArrayList<>();

        for (SavedState savedState : revisions) {
            results.add(new HistoryResult(savedState.getName(), savedState.getTimestamp(), savedState.getType().toString(),
                    savedState.getMsg(), Collections.emptyList()));
        }
        return results;
    }

    @Override
    public InstallationChanges revisionDetails(String revision) throws MetadataException {
        Objects.requireNonNull(revision);
        final InstallationHistoryAction historyAction = actionFactory.getHistoryAction();
        final org.wildfly.prospero.api.InstallationChanges changes = historyAction.getRevisionChanges(new SavedState(revision));

        if (changes.isEmpty()) {
            return new InstallationChanges(Collections.emptyList(), Collections.emptyList());
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
    public void prepareRevert(String revision, Path targetDir, List<Repository> repositories) throws Exception {
        Objects.requireNonNull(revision);
        Objects.requireNonNull(targetDir);
        final InstallationHistoryAction historyAction = actionFactory.getHistoryAction();
        historyAction.prepareRevert(new SavedState(revision), actionFactory.mavenOptions,
                map(repositories, ProsperoInstallationManager::mapRepository), targetDir);
    }

    @Override
    public boolean prepareUpdate(Path targetDir, List<Repository> repositories) throws Exception {
        try (UpdateAction prepareUpdateAction = actionFactory.getUpdateAction(map(repositories, ProsperoInstallationManager::mapRepository))) {
            try {
                return prepareUpdateAction.buildUpdate(targetDir);
            } catch (SignatureValidator.SignatureException e) {
                if (e.getSignatureResult().getResult() == SignatureResult.Result.NO_MATCHING_CERT) {
                    throw new MissingSignatureException(e.getMessage(), e, e.getMissingSignature());
                } else {
                    throw e;
                }
            }
        }
    }

    @Override
    public Collection<FileConflict> verifyCandidate(Path candidatePath, CandidateType candidateType) throws Exception {
        final ApplyCandidateAction applyCandidateAction = actionFactory.getApplyCandidateAction(candidatePath);
        final ApplyCandidateAction.Type operation;
        switch (candidateType) {
            case UPDATE:
                operation = ApplyCandidateAction.Type.UPDATE;
                break;
            case REVERT:
                operation = ApplyCandidateAction.Type.REVERT;
                break;
            default:
                throw new IllegalArgumentException("Unsupported candidate type: " + candidateType);
        }

        final ApplyCandidateAction.ValidationResult validationResult = applyCandidateAction.verifyCandidate(operation);
        switch (validationResult) {
            case OK:
                // we're good, continue
                break;
            case STALE:
                throw ProsperoLogger.ROOT_LOGGER.staleCandidate(installationDir, candidatePath);
            case NO_CHANGES:
                throw ProsperoLogger.ROOT_LOGGER.noChangesAvailable(installationDir, candidatePath);
            case NOT_CANDIDATE:
                throw ProsperoLogger.ROOT_LOGGER.notCandidate(candidatePath);
            case WRONG_TYPE:
                throw ProsperoLogger.ROOT_LOGGER.wrongCandidateOperation(candidatePath, operation);
            default:
                // unexpected validation type - include the error in the description
                throw new InvalidUpdateCandidateException(String.format("The candidate server %s is invalid - %s.", candidatePath, validationResult));
        }

        return map(applyCandidateAction.getConflicts(), ProsperoInstallationManager::mapFileConflict);
    }

    @Override
    public void acceptTrustedCertificates(InputStream certificate) throws Exception {
        Objects.requireNonNull(certificate);
        try (CertificateAction certificateAction = actionFactory.getCertificateAction()) {
            certificateAction.importCertificate(new PGPPublicKey("Imported cert", certificate));
        }
    }

    @Override
    public void revokeTrustedCertificate(String keyID) throws Exception {
        try (CertificateAction certificateAction = actionFactory.getCertificateAction()) {
            certificateAction.removeCertificate(new PGPKeyId(keyID));
        }
    }

    @Override
    public Collection<TrustCertificate> listTrustedCertificates() throws Exception {
        try (CertificateAction certificateAction = actionFactory.getCertificateAction()) {
            return certificateAction.listCertificates().stream()
                    .map(ProsperoInstallationManager::mapCertificate)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public TrustCertificate parseCertificate(InputStream is) throws Exception {
        return mapCertificate(PGPPublicKeyInfo.parse(is, "Imported cert"));
    }

    private static FileConflict mapFileConflict(org.wildfly.prospero.api.FileConflict fileConflict) {
        return new FileConflict(Path.of(fileConflict.getRelativePath()), map(fileConflict.getUserChange()), map(fileConflict.getUpdateChange()), fileConflict.getResolution() == org.wildfly.prospero.api.FileConflict.Resolution.UPDATE);
    }

    private static FileConflict.Status map(org.wildfly.prospero.api.FileConflict.Change change) {
        switch (change) {
            case MODIFIED:
                return FileConflict.Status.MODIFIED;
            case ADDED:
                return FileConflict.Status.ADDED;
            case REMOVED:
                return FileConflict.Status.REMOVED;
            case NONE:
                return FileConflict.Status.NONE;
            default:
                throw new IllegalArgumentException("Unknown file conflict change: " + change);
        }
    }

    @Override
    public List<ArtifactChange> findUpdates(List<Repository> repositories) throws Exception {
        try (UpdateAction updateAction = actionFactory.getUpdateAction(map(repositories, ProsperoInstallationManager::mapRepository))) {
            final UpdateSet updates = updateAction.findUpdates();
            return updates.getArtifactUpdates().stream()
                    .map(ProsperoInstallationManager::mapArtifactChange)
                    .collect(Collectors.toList());
        } catch (SignatureValidator.SignatureException e) {
            if (e.getSignatureResult().getResult() == SignatureResult.Result.NO_MATCHING_CERT) {
                throw new MissingSignatureException(e.getMessage(), e, e.getMissingSignature());
            } else {
                throw e;
            }
        }
    }

    @Override
    public Collection<Channel> listChannels() throws OperationException {
        try (MetadataAction metadataAction = actionFactory.getMetadataAction()) {
            return metadataAction.getChannels().stream()
                    .map(ProsperoInstallationManager::mapChannel)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void removeChannel(String channelName) throws OperationException {
        try (MetadataAction metadataAction = actionFactory.getMetadataAction()) {
            metadataAction.removeChannel(channelName);
        }
    }

    @Override
    public void addChannel(Channel channel) throws OperationException {
        try (MetadataAction metadataAction = actionFactory.getMetadataAction()) {
            metadataAction.addChannel(mapChannel(channel));
        }
    }

    @Override
    public void changeChannel(Channel newChannel) throws OperationException {
        if (newChannel.getName() == null || newChannel.getName().isEmpty()) {
            throw ProsperoLogger.ROOT_LOGGER.emptyChannelName();
        }
        try (MetadataAction metadataAction = actionFactory.getMetadataAction()) {
            metadataAction.changeChannel(newChannel.getName(), mapChannel(newChannel));
        }
    }

    @Override
    public Path createSnapshot(Path targetPath) throws Exception {
        final Path snapshotPath;
        if (!Files.exists(targetPath)) {
            if (targetPath.toString().toLowerCase().endsWith(".zip")) {
                snapshotPath = targetPath.toAbsolutePath();
            } else {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                snapshotPath = targetPath.resolve("im-snapshot-" + timestamp + ".zip").toAbsolutePath();
            }
        } else if (Files.isDirectory(targetPath)) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            snapshotPath = targetPath.resolve("im-snapshot-" + timestamp + ".zip").toAbsolutePath();
        } else {
            throw ProsperoLogger.ROOT_LOGGER.fileAlreadyExists(targetPath);
        }

        final InstallationExportAction installationExportAction = actionFactory.getInstallationExportAction();
        installationExportAction.export(snapshotPath);

        return snapshotPath;
    }

    @Override
    public String generateApplyUpdateCommand(Path scriptHome, Path candidatePath) throws OperationNotAvailableException {
        final boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
        return this.generateApplyUpdateCommand(scriptHome, candidatePath, isWindows?OsShell.WindowsBash:OsShell.Linux);
    }

    @Override
    public String generateApplyRevertCommand(Path scriptHome, Path candidatePath) throws OperationNotAvailableException {
        final boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
        return this.generateApplyRevertCommand(scriptHome, candidatePath, isWindows?OsShell.WindowsBash:OsShell.Linux);
    }

    @Override
    public String generateApplyUpdateCommand(Path scriptHome, Path candidatePath, OsShell shell) throws OperationNotAvailableException {
        return generateApplyUpdateCommand(scriptHome, candidatePath, shell, false);
    }

    @Override
    public String generateApplyRevertCommand(Path scriptHome, Path candidatePath, OsShell shell) throws OperationNotAvailableException {
        return generateApplyUpdateCommand(scriptHome, candidatePath, shell, false);
    }

    @Override
    public String generateApplyUpdateCommand(Path scriptHome, Path candidatePath, OsShell shell, boolean noConflictsOnly) throws OperationNotAvailableException {
        final Optional<CliProvider> cliProviderLoader = ServiceLoader.load(CliProvider.class).findFirst();
        if (cliProviderLoader.isEmpty()) {
            throw new OperationNotAvailableException("Installation manager does not support CLI operations.");
        }

        final CliProvider cliProvider = cliProviderLoader.get();
        return escape(scriptHome.resolve(cliProvider.getScriptName(shell))) + " " + cliProvider.getApplyUpdateCommand(installationDir, candidatePath, false);
    }

    @Override
    public String generateApplyRevertCommand(Path scriptHome, Path candidatePath, OsShell shell, boolean noConflictsOnly) throws OperationNotAvailableException {
        final Optional<CliProvider> cliProviderLoader = ServiceLoader.load(CliProvider.class).findFirst();
        if (cliProviderLoader.isEmpty()) {
            throw new OperationNotAvailableException("Installation manager does not support CLI operations.");
        }

        final CliProvider cliProvider = cliProviderLoader.get();
        return escape(scriptHome.resolve(cliProvider.getScriptName(shell))) + " " + cliProvider.getApplyRevertCommand(installationDir, candidatePath, noConflictsOnly);
    }

    @Override
    public Collection<ManifestVersion> getInstalledVersions() throws MetadataException {
        try (MetadataAction metadataAction = actionFactory.getMetadataAction()) {
            final ManifestVersionRecord versionRecord = metadataAction.getChannelVersions();
            return Stream.concat(
                    versionRecord.getMavenManifests().stream()
                            .map(m->new ManifestVersion(m.getGroupId()+":"+m.getArtifactId(), m.getDescription(), m.getVersion(), ManifestVersion.Type.MAVEN)),
                    versionRecord.getUrlManifests().stream()
                            .map(m->new ManifestVersion(m.getUrl(), m.getDescription(), m.getHash(), ManifestVersion.Type.URL))
                    )
                    .collect(Collectors.toList());
        }
    }

    @Override
    public Collection<InputStream> downloadRequiredCertificates() throws Exception {
        ArrayList<InputStream> missingCerts = new ArrayList<>();
        try (MetadataAction metadataAction = actionFactory.getMetadataAction();
             // TODO: replace with actionFactory
             CertificateAction certificateAction = new CertificateAction(installationDir)) {
            final List<String> urls = metadataAction.getChannels().stream()
                    .map(org.wildfly.channel.Channel::getGpgUrls)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            int counter = 0;
            final Set<PGPKeyId> discoveredKeys = new HashSet<>();
            for (String urlText : urls) {
                if (this.mavenOptions.isOffline() && !(urlText.startsWith("file") || urlText.startsWith("classpath"))) {
                    // ignore remote certificates if we're offline
                    continue;
                }
                // resolve cert URLs
                final URL url = new URL(urlText);
                // parse cert files
                final TrustCertificate tc = parseCertificate(url.openStream());
                final PGPKeyId keyId = new PGPKeyId(tc.getKeyID());
                // check keyIDs of the certs vs. trusted certs
                if (!discoveredKeys.contains(keyId) && certificateAction.listCertificates().stream()
                        .noneMatch(c->c.getKeyID().equals(keyId))) {
                    // if any are missing, return them
                    missingCerts.add(url.openStream());

                    discoveredKeys.add(keyId);
                }
            }

        }
        return missingCerts;
    }

    private String escape(Path absolutePath) {
        return "\"" + absolutePath.toString() + "\"";
    }

    private static Channel mapChannel(org.wildfly.channel.Channel channel) {
        if (channel.getManifestCoordinate() == null) {
            return new Channel(channel.getName(), map(channel.getRepositories(), ProsperoInstallationManager::mapRepository));
        } else if (channel.getManifestCoordinate().getUrl() != null) {
            return new Channel(channel.getName(), map(channel.getRepositories(), ProsperoInstallationManager::mapRepository), channel.getManifestCoordinate().getUrl());
        } else if (channel.getManifestCoordinate().getMaven() != null) {
            return new Channel(channel.getName(), map(channel.getRepositories(), ProsperoInstallationManager::mapRepository), toGav(channel.getManifestCoordinate().getMaven()));
        } else {
            return new Channel(channel.getName(), map(channel.getRepositories(), ProsperoInstallationManager::mapRepository));
        }
    }

    private static String toGav(MavenCoordinate coord) {
        final String ga = coord.getGroupId() + ":" + coord.getArtifactId();
        if (coord.getVersion() != null && !coord.getVersion().isEmpty()) {
            return ga + ":" + coord.getVersion();
        }
        return ga;
    }

    private static org.wildfly.channel.Channel mapChannel(Channel channel) {
        return new org.wildfly.channel.Channel(channel.getName(), null, null,
                map(channel.getRepositories(), ProsperoInstallationManager::mapRepository), toManifestCoordinate(channel),
                null, null);
    }

    private static ChannelManifestCoordinate toManifestCoordinate(Channel c) {
        if (c.getManifestUrl().isPresent()) {
            return new ChannelManifestCoordinate(c.getManifestUrl().get());
        } else if (c.getManifestCoordinate().isPresent()) {
            final String[] coordinate = c.getManifestCoordinate().get().split(":");
            if (coordinate.length == 3) {
                return new ChannelManifestCoordinate(coordinate[0], coordinate[1], coordinate[2]);
            } else {
                return new ChannelManifestCoordinate(coordinate[0], coordinate[1]);
            }
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

        switch (change.getStatus()) {
            case ADDED:
                return new ChannelChange(oldChannel, newChannel, ChannelChange.Status.ADDED);
            case REMOVED:
                return new ChannelChange(oldChannel, newChannel, ChannelChange.Status.REMOVED);
            default:
                return new ChannelChange(oldChannel, newChannel, ChannelChange.Status.MODIFIED);
        }
    }

    private static TrustCertificate mapCertificate(PGPPublicKeyInfo keyInfo) {
        return new TrustCertificate(keyInfo.getKeyID().getHexKeyID(),
                keyInfo.getFingerprint(),
                String.join("; ", keyInfo.getIdentity()),
                keyInfo.getStatus().toString());
    }

    ActionFactory getActionFactory() {
        return actionFactory;
    }

    protected static class ActionFactory {

        private final Path server;
        private final org.wildfly.prospero.api.MavenOptions mavenOptions;

        private ActionFactory(Path server, org.wildfly.prospero.api.MavenOptions mavenOptions) {
            this.server = server;
            this.mavenOptions = mavenOptions;
        }

        protected InstallationHistoryAction getHistoryAction() {
            return new InstallationHistoryAction(server, null);
        }

        protected UpdateAction getUpdateAction(List<org.wildfly.channel.Repository> repositories) throws OperationException, ProvisioningException {
            return new UpdateAction(server, mavenOptions, null, repositories);
        }

        protected MetadataAction getMetadataAction() throws MetadataException {
            return new MetadataAction(server);
        }

        protected InstallationExportAction getInstallationExportAction() {
            return new InstallationExportAction(server);
        }

        protected ApplyCandidateAction getApplyCandidateAction(Path candidateDir) throws ProvisioningException, OperationException {
            return new ApplyCandidateAction(server, candidateDir);
        }

        org.wildfly.prospero.api.MavenOptions getMavenOptions() {
            return mavenOptions;
        }

        public CertificateAction getCertificateAction() throws MetadataException {
            return new CertificateAction(server);
        }
    }
}
