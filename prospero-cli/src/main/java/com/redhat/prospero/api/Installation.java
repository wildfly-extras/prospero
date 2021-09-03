package com.redhat.prospero.api;

import java.io.File;
import java.util.List;

public interface Installation {

   void installPackage(File packageFile) throws PackageInstallationException;

   void installArtifact(Artifact definition, File archiveFile) throws PackageInstallationException;

   void updateArtifact(Artifact oldArtifact,
                       Artifact newArtifact,
                       File artifactFile) throws PackageInstallationException;

   Manifest getManifest();

   List<Channel> getChannels();
}
