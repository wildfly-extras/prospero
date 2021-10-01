package com.redhat.prospero.api;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

public interface Resolver {

    ArtifactResult resolve(Artifact artifact) throws ArtifactResolutionException;
    VersionRangeResult getVersionRange(Artifact artifact) throws VersionRangeResolutionException;
}
