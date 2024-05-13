package org.wildfly.prospero.cli;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.Channel;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.exceptions.ChannelDefinitionException;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.util.List;

public class ChannelUtils {

    public static VersionResolverFactory createVersionResolverFactory(MavenSessionManager mavenSessionManager) {
        final RepositorySystem repositorySystem = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession repositorySystemSession = mavenSessionManager.newRepositorySystemSession(
                repositorySystem);
        return new VersionResolverFactory(repositorySystem, repositorySystemSession);
    }

    public static List<Channel> resolveChannels(ProvisioningDefinition provisioningDefinition, MavenOptions mavenOptions)
            throws ArgumentParsingException, ProvisioningException, NoChannelException, ChannelDefinitionException {
        final MavenSessionManager mavenSessionManager = new MavenSessionManager(mavenOptions);
        final VersionResolverFactory versionResolverFactory = createVersionResolverFactory(mavenSessionManager);
        final List<Channel> channels = provisioningDefinition.resolveChannels(versionResolverFactory);
        return channels;
    }

}
