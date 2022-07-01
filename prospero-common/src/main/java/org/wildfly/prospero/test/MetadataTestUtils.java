package org.wildfly.prospero.test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.model.ChannelRef;

public final class MetadataTestUtils {

    private MetadataTestUtils() {
    }

    public static Channel createManifest(Collection<Stream> streams) {
        return new Channel("manifest", null, null, null, streams);
    }

    public static InstallationMetadata createInstallationMetadata(Path installation) throws MetadataException {
        return createInstallationMetadata(installation, createManifest(null), Collections.emptyList(), Collections.emptyList());
    }

    public static InstallationMetadata createInstallationMetadata(Path installation, List<ChannelRef> channels,
            List<RemoteRepository> repositories) throws MetadataException {
        return createInstallationMetadata(installation, createManifest(null), channels, repositories);
    }

    public static InstallationMetadata createInstallationMetadata(Path installation, Channel manifest, List<ChannelRef> channels,
            List<RemoteRepository> repositories) throws MetadataException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtils.deleteQuietly(installation.toFile())));
        final InstallationMetadata metadata = new InstallationMetadata(installation, manifest, channels, repositories);
        metadata.writeFiles();
        return metadata;
    }
}
