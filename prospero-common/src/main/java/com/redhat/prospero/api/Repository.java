package com.redhat.prospero.api;

import java.io.File;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.artifact.Artifact;
import org.wildfly.channel.UnresolvedMavenArtifactException;

public interface Repository {

    File resolve(Artifact artifact) throws UnresolvedMavenArtifactException;
    Artifact resolveLatestVersionOf(Artifact artifact) throws UnresolvedMavenArtifactException;

    VersionRangeResult getVersionRange(Artifact artifact) throws UnresolvedMavenArtifactException;
}
