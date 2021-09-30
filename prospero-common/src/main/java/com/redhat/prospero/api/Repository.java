package com.redhat.prospero.api;

import java.io.File;

import com.redhat.prospero.xml.XmlException;

public interface Repository {

    File resolve(Artifact artifact) throws ArtifactNotFoundException;

    Artifact findLatestVersionOf(Artifact artifact);
}
