package com.redhat.prospero.api;

import java.io.File;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;

public interface Installation {

    void installArtifact(Artifact definition, File archiveFile) throws PackageInstallationException;

    void updateArtifact(Artifact oldArtifact,
                        Artifact newArtifact,
                        File artifactFile) throws PackageInstallationException;

    Manifest getManifest();

    List<Channel> getChannels();
}
