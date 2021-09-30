package com.redhat.prospero.api;

import java.io.File;

import com.redhat.prospero.xml.XmlException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.jboss.galleon.universe.maven.MavenUniverseException;

public interface Repository {

    File resolve(Artifact artifact) throws ArtifactNotFoundException;

    Artifact findLatestVersionOf(Artifact artifact);

    VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException;
}
