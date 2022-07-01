package org.wildfly.prospero.actions;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.model.ProvisioningConfig;
import org.wildfly.prospero.model.RepositoryRef;

/**
 * Metadata related actions wrapper.
 */
public class MetadataActions {

    private final Path installation;

    public MetadataActions(Path installation) {
        this.installation = installation;
    }

    /**
     * Adds a remote maven repository to an installation.
     */
    public void addRepository(String name, URL url) throws MetadataException {
        InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        ProvisioningConfig prosperoConfig = installationMetadata.getProsperoConfig();
        if (prosperoConfig.addRepository(new RepositoryRef(name, url.toString()))) {
            installationMetadata.updateProsperoConfig(prosperoConfig);
        } else {
            throw Messages.MESSAGES.repositoryExists(name, url);
        }
    }

    /**
     * Removes a remote maven repository from an installation.
     */
    public void removeRepository(String id) throws MetadataException {
        InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        ProvisioningConfig prosperoConfig = installationMetadata.getProsperoConfig();
        prosperoConfig.removeRepository(id);
        installationMetadata.updateProsperoConfig(prosperoConfig);
    }

    /**
     * Retrieves maven remote repositories used by an installation.
     */
    public List<RepositoryRef> getRepositories() throws MetadataException {
        InstallationMetadata installationMetadata = new InstallationMetadata(installation);
        ProvisioningConfig prosperoConfig = installationMetadata.getProsperoConfig();
        return prosperoConfig.getRepositories();
    }

}
