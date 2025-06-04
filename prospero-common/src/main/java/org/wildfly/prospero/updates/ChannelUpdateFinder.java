package org.wildfly.prospero.updates;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.prospero.api.ChannelVersion;

public class ChannelUpdateFinder {
    private final RepositorySystemSession session;
    private final RepositorySystem system;

    public ChannelUpdateFinder(RepositorySystem system, RepositorySystemSession session) {
        this.session = session;
        this.system = system;
    }

    public Collection<ChannelVersion> findNewerVersions(Channel channel, ChannelVersion channelVersion, boolean allowDowngrades) throws VersionRangeResolutionException, ArtifactResolutionException, MalformedURLException {
        // TODO: verify it's a maven manifest and that it has a version

        final ChannelManifestCoordinate coord = channel.getManifestCoordinate();
        Artifact artifact = new DefaultArtifact(
                coord.getGroupId(),
                coord.getArtifactId(),
                coord.getClassifier(),
                coord.getExtension(),
                "(" + (allowDowngrades ? "0" : channelVersion.getPhysicalVersion()) + ",)");
        final List<RemoteRepository> repos = channel.getRepositories().stream()
                .map(r -> new RemoteRepository.Builder(r.getId(), "default", r.getUrl()).build())
                .toList();
        VersionRangeRequest req = new VersionRangeRequest(artifact, repos, null);
        final VersionRangeResult versionRangeResult = system.resolveVersionRange(session, req);

        final ArrayList<ArtifactRequest> requests = new ArrayList<>();
        for (Version version : versionRangeResult.getVersions()) {
            requests.add(new ArtifactRequest(artifact.setVersion(version.toString()), repos, null));
        }
        final List<ArtifactResult> artifactResults = system.resolveArtifacts(session, requests);

        final ArrayList<ChannelVersion> channelVersions = new ArrayList<>();
        for (ArtifactResult artifactResult : artifactResults) {
            final ChannelManifest manifest = ChannelManifestMapper.from(artifactResult.getArtifact().getFile().toURI().toURL());
            final String logicalVersion = manifest.getLogicalVersion();
            channelVersions.add(new ChannelVersion.Builder()
                    .setChannelName(channel.getName())
                    .setPhysicalVersion(artifactResult.getArtifact().getVersion())
                    .setLogicalVersion(logicalVersion)
                    .build());
        }

        return channelVersions;
    }
}
