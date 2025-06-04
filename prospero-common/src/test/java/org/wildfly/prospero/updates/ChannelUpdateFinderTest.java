package org.wildfly.prospero.updates;

import java.util.Collection;

import org.eclipse.aether.RepositorySystem;
import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.prospero.api.ChannelVersion;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

public class ChannelUpdateFinderTest {

    @Test
    public void listUpdatesOfManifest() throws Exception {
        final MavenSessionManager msm = new MavenSessionManager();
        final RepositorySystem repositorySystem = msm.newRepositorySystem();
        final Collection<ChannelVersion> newerVersions = new ChannelUpdateFinder(repositorySystem, msm.newRepositorySystemSession(repositorySystem))
                .findNewerVersions(new Channel.Builder()
                        .setName("test-channel")
                        .setManifestCoordinate("org.wildfly.channels", "wildfly-ee")
                        .addRepository("central", "https://repo1.maven.org/maven2/")
                        .build(), new ChannelVersion.Builder().setChannelName("test-channel").setPhysicalVersion("35.0.0.Final").build(),
                        false);

        for (ChannelVersion newerVersion : newerVersions) {
            System.out.println(newerVersion);
        }
    }

}