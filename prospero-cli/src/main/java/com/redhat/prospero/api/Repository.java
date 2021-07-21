package com.redhat.prospero.api;

import java.io.File;

import com.redhat.prospero.xml.XmlException;

public interface Repository {

   File resolve(Gav artifact) throws ArtifactNotFoundException;

   Gav findLatestVersionOf(Gav artifact);

   ArtifactDependencies resolveDescriptor(Gav latestVersion) throws XmlException;
}
