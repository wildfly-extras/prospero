/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.prospero.actions;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.util.HashUtils;
import org.wildfly.channel.ArtifactTransferException;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.gpg.GpgSignatureValidator;
import org.wildfly.channel.gpg.GpgSignatureValidatorListener;
import org.wildfly.channel.spi.ArtifactIdentifier;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.ProvisioningProgressEvent;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.ArtifactCache;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.signatures.ConfirmingKeystoreAdapter;
import org.wildfly.prospero.signatures.KeystoreManager;
import org.wildfly.prospero.signatures.PGPPublicKeyInfo;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

class VerifyServerOriginAction {

    private final Path installPath;
    private final MavenSessionManager mavenSessionManager;
    private final CertificateAction.VerificationListener console;

    VerifyServerOriginAction(Path installPath, MavenSessionManager mavenSessionManager, CertificateAction.VerificationListener console) {
        this.installPath = installPath;
        this.mavenSessionManager = mavenSessionManager;
        this.console = console;
    }

    VerificationResult verify() throws ProvisioningException, OperationException {
        final Path refServerDir;
        try {
            refServerDir = Files.createTempDirectory("prospero-candidate");
            if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                ProsperoLogger.ROOT_LOGGER.debug("Created temporary folder in " + refServerDir);
            }
        } catch (IOException e) {
            throw ProsperoLogger.ROOT_LOGGER.unableToCreateTemporaryFile(e);
        }

        final List<VerificationResult.InvalidBinary> invalidBinaries = new ArrayList<>();
        final List<Path> modifiedPaths = new ArrayList<>();
        final Set<PGPPublicKeyInfo> trustedCertificates = new HashSet<>();
        try {
            final GalleonEnvironment env = buildReferenceServer(refServerDir);

            // now we verify that all the artifacts in the generated server are correctly signed
            // we don't verify the artifacts during the provisioning to catch all unsigned artifacts
            validateBinaries(env, refServerDir, invalidBinaries, trustedCertificates);

            // compare the generated server with original server to find any locally corrupted/unsigned files
            // any file that is not present in the generated reference server is considered not signed - as we don't know the GAV
            findLocallyModifiedFiles(refServerDir, invalidBinaries, modifiedPaths);

        } catch (IOException e) {
            throw new RuntimeException("Unable to perform I/O operation", e);
        } finally {
            // cleanup after ourselves - remove the generated server
            if (refServerDir != null) {
                if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.debug("Removing temporary folder in " + refServerDir);
                }
                FileUtils.deleteQuietly(refServerDir.toFile());
            }
        }

        // re-provision the server using the cache from existing server and accepting channel setting
        return new VerificationResult(invalidBinaries, modifiedPaths, trustedCertificates);
    }

    private void findLocallyModifiedFiles(Path refServerDir, List<VerificationResult.InvalidBinary> invalidBinaries, List<Path> modifiedPaths) throws IOException {
        console.checkingModifiedFilesStarted();
        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
            ProsperoLogger.ROOT_LOGGER.debug("Starting checking local file modifications");
        }

        Files.walkFileTree(installPath, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                final Path relativePath = installPath.relativize(dir);
                if (relativePath.startsWith(ProsperoMetadataUtils.METADATA_DIR) || relativePath.startsWith(Constants.PROVISIONED_STATE_DIR)){
                    return FileVisitResult.SKIP_SUBTREE;
                } else {
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public FileVisitResult visitFile(Path originalFile, BasicFileAttributes attrs) throws IOException {
                final Path relativePath = installPath.relativize(originalFile);
                final Path referenceFile = refServerDir.resolve(relativePath);

                if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.trace("Comparing " + originalFile + " and " + referenceFile);
                }

                if (!Files.exists(referenceFile)) {
                    if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                        ProsperoLogger.ROOT_LOGGER.debug("File " + relativePath + " doesn't exist in the reference server");
                    }

                    invalidBinaries.add(new VerificationResult.InvalidBinary(relativePath, null, SignatureResult.Result.NO_SIGNATURE));
                } else if (!HashUtils.hashFile(referenceFile).equals(HashUtils.hashFile(originalFile))) {
                    // check that it has not already been identified as corrupted binary
                    if (invalidBinaries.stream().map(VerificationResult.InvalidBinary::getPath).noneMatch(p->p.equals(relativePath))) {
                        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                            ProsperoLogger.ROOT_LOGGER.debug("File " + relativePath + " has local modifications");
                        }
                        modifiedPaths.add(relativePath);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        console.checkingModifiedFilesFinished();
    }

    private void validateBinaries(GalleonEnvironment env, Path refServerDir, List<VerificationResult.InvalidBinary> invalidBinaries, Set<PGPPublicKeyInfo> trustedCertificates)
            throws IOException, MetadataException {
        console.validatingComponentsStarted();
        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
            ProsperoLogger.ROOT_LOGGER.debug("Starting validation of server components");
        }

        final ChannelSession channelSession = env.getChannelSession();

        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
            ProsperoLogger.ROOT_LOGGER.debug("Using artifacts recorded in " + refServerDir);
        }
        final ArtifactCache cache = ArtifactCache.getInstance(refServerDir);
        final List<ArtifactCache.CachedArtifact> cachedArtifacts = cache.listArtifacts();

        final Path keystorePath = installPath.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve("keyring.gpg");
        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
            ProsperoLogger.ROOT_LOGGER.debug("Using signatures trusted by " + keystorePath);
        }
        final GpgSignatureValidator validator = new GpgSignatureValidator(new ConfirmingKeystoreAdapter(
                KeystoreManager.keystoreFor(keystorePath),
                (desc) -> false));

        // use the listener to record trusted signatures
        validator.addListener(new GpgSignatureValidatorListener() {
            @Override
            public void artifactSignatureCorrect(ArtifactIdentifier artifact, PGPPublicKey publicKey) {
                trustedCertificates.add(PGPPublicKeyInfo.parse(publicKey));
            }

            @Override
            public void artifactSignatureInvalid(ArtifactIdentifier artifact, PGPPublicKey publicKey) {
                // ignore
            }
        });

        cachedArtifacts.stream().parallel().forEach(artifact-> {
            // now, let's try to validate this artifact
            // need to first download the signatures
            final MavenArtifact signature = downloadSignature(artifact, channelSession);
            if (signature == null) {
                if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.debug("Not able to resolve signature for " + artifact);
                }

                final Path relativePath = refServerDir.relativize(artifact.getPath());
                invalidBinaries.add(new VerificationResult.InvalidBinary(relativePath, artifact.getGav(), SignatureResult.Result.NO_SIGNATURE));
                return;
            }

            final ArtifactIdentifier.MavenResource artifactIdent = new ArtifactIdentifier.MavenResource(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getExtension(),
                    artifact.getClassifier(),
                    artifact.getVersion()
            );

            final Path relativePath = refServerDir.relativize(artifact.getPath());
            Path checkedPath = installPath.resolve(relativePath);
            // check the binary in the original server if available. If not, check downloaded to verify e.g. feature pack is valid
            if (!Files.exists(checkedPath)) {
                checkedPath = artifact.getPath();
            }
            if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                ProsperoLogger.ROOT_LOGGER.trace("Verifying signature of " + checkedPath);
            }

            final SignatureResult signatureResult;
            try {
                signatureResult = validator.validateSignature(
                        artifactIdent,
                        new FileInputStream(checkedPath.toFile()),
                        new FileInputStream(signature.getFile()), Collections.emptyList());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                ProsperoLogger.ROOT_LOGGER.trace("Artifact " + artifactIdent + " verification result: " + signatureResult);
            }
            if (signatureResult.getResult() != SignatureResult.Result.OK) {
                invalidBinaries.add(new VerificationResult.InvalidBinary(relativePath, artifact.getGav(), signatureResult.getResult(), signatureResult.getKeyId()));
            }
        });
        console.validatingComponentsFinished();
    }

    private static MavenArtifact downloadSignature(ArtifactCache.CachedArtifact artifact, ChannelSession channelSession) {
        try {
            if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                ProsperoLogger.ROOT_LOGGER.trace("Downloading signature for " + artifact);
            }
            return channelSession.resolveDirectMavenArtifact(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getExtension() + ".asc",
                    artifact.getClassifier(),
                    artifact.getVersion());
        } catch (ArtifactTransferException e) {
            return null;
        }
    }

    private GalleonEnvironment buildReferenceServer(Path tempServerDir) throws ProvisioningException, OperationException {
        console.provisionReferenceServerStarted();

        final ProsperoConfig config;
        final GalleonProvisioningConfig galleonProvisioningConfig;
        try (InstallationMetadata metadata = InstallationMetadata.loadInstallation(installPath)) {
            if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                ProsperoLogger.ROOT_LOGGER.debug("Loading existing server metadata from " + installPath);
            }

            config = metadata.getProsperoConfig();
            galleonProvisioningConfig = metadata.getGalleonProvisioningConfig();
        }

        final AdapterConsole adapterConsole = new AdapterConsole();

        final List<Channel> channels = config.getChannels().stream()
                .map(c->new Channel.Builder(c)
                        .setGpgCheck(false)
                        .setManifestUrl(getExistingManifestUrl())
                        .build())
                .collect(Collectors.toList());
        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
            ProsperoLogger.ROOT_LOGGER.debug("Using channel definition " + channels);
        }

        final GalleonEnvironment env = GalleonEnvironment
                .builder(tempServerDir, channels, mavenSessionManager, false)
                .setConsole(adapterConsole)
                .build();

        try (PrepareCandidateAction prepareCandidateAction = new PrepareCandidateAction(installPath, mavenSessionManager, config, adapterConsole)) {
            if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                ProsperoLogger.ROOT_LOGGER.debug("Provisioning reference server in " + tempServerDir);
            }

            prepareCandidateAction.buildCandidate(tempServerDir, env, ApplyCandidateAction.Type.UPDATE, galleonProvisioningConfig);

            if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                ProsperoLogger.ROOT_LOGGER.debug("Finished provisioning reference server in " + tempServerDir);
            }
        }
        console.provisionReferenceServerFinished();
        return env;
    }

    private URL getExistingManifestUrl() {
        try {
            return ProsperoMetadataUtils.manifestPath(installPath).toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private class AdapterConsole implements Console {
        @Override
        public void progressUpdate(ProvisioningProgressEvent update) {
            console.progressUpdate(update);
        }

        @Override
        public void println(String text) {
            // noop
        }

        @Override
        public boolean acceptPublicKey(String key) {
            // always reject unknown keys
            return false;
        }
    }
}
