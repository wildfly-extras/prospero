/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.installation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.redhat.prospero.api.Artifact;
import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Installation;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.api.PackageInstallationException;
import com.redhat.prospero.xml.ManifestXmlSupport;
import com.redhat.prospero.xml.ModuleXmlSupport;
import com.redhat.prospero.xml.XmlException;
import org.apache.commons.io.FileUtils;

public class LocalInstallation implements Installation {

    private final Manifest manifest;
    private final Path base;
    private final Modules modules;
    private final List<Channel> channels;

    public LocalInstallation(Path base) throws XmlException, IOException {
        this.base = base;
        manifest = ManifestXmlSupport.parse(base.resolve("manifest.xml").toFile());
        modules = new Modules(base);
        this.channels = Channel.readChannels(base.resolve("channels.json"));
    }

    @Override
    public void installArtifact(Artifact definition, File archiveFile) throws PackageInstallationException {
        //  find in modules
        Collection<Path> updates = modules.find(definition);

        if (updates.isEmpty()) {
            throw new PackageInstallationException("Artifact " + definition.getFileName() + " not found");
        }

        //  drop jar into module folder
        updates.forEach(p -> {
            try {
                FileUtils.copyFile(archiveFile, p.getParent().resolve(archiveFile.getName()).toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void updateArtifact(Artifact oldArtifact, Artifact newArtifact, File artifactFile) throws PackageInstallationException {
        Collection<Path> updates = modules.find(oldArtifact);

        if (updates.isEmpty()) {
            throw new PackageInstallationException("Artifact " + oldArtifact.getFileName() + " not found");
        }

        for (Path module : updates) {
            // copy the new artifact
            Path target = module.getParent();
            try {
                FileUtils.copyFile(artifactFile, target.resolve(newArtifact.getFileName()).toFile());
            } catch (IOException e) {
                throw new PackageInstallationException("Unable to install package " + newArtifact, e);
            }

            // update model.xml
            try {
                ModuleXmlSupport.INSTANCE.updateVersionInModuleXml(module, oldArtifact, newArtifact);
            } catch (XmlException e) {
                throw new PackageInstallationException("Unable to write changes in module xml", e);
            }

            // update manifest.xml
            manifest.updateVersion(newArtifact);
        }
    }

    @Override
    public Manifest getManifest() {
        return manifest;
    }

    @Override
    public List<Channel> getChannels() {
        return channels;
    }

    public void registerUpdates(Set<Artifact> artifacts) {
        for (Artifact artifact : artifacts) {
            manifest.updateVersion(artifact);
        }
    }
}
