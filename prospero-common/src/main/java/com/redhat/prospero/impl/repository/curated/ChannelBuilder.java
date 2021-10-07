package com.redhat.prospero.impl.repository.curated;

import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.impl.repository.DefaultResolver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class ChannelBuilder {

    final private RepositorySystem repoSystem;
    private final RepositorySystemSession repoSession;

    public ChannelBuilder(RepositorySystem repoSystem, RepositorySystemSession repoSession) {
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
    }

    public Repository buildChannelRepository(Channel channel) throws IOException {
        final String channelDefinitionUrl = channel.getUrl();
        final ChannelDefinition curatedPolicies = new ChannelDefinitionParser().parsePolicyFile(new URL(channelDefinitionUrl));
        final List<RemoteRepository> repositories = Arrays.asList(toRepository(channel.getName(), curatedPolicies.getRepositoryUrl()));
        final DefaultResolver resolver = new DefaultResolver(repositories, repoSystem, repoSession);
        return new CuratedMavenRepository(resolver, curatedPolicies.getChannelRules());
    }

    private RemoteRepository toRepository(String channel, String url) {
        return new RemoteRepository.Builder(channel, "default", url).build();
    }
}
