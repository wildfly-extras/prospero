package com.redhat.prospero.impl.repository.curated;

import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.impl.repository.DefaultResolver;
import com.redhat.prospero.impl.repository.combined.CombinedMavenRepository;
import com.redhat.prospero.impl.repository.restore.RestoringMavenRepository;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ChannelBuilder {

    final private RepositorySystem repoSystem;
    private final RepositorySystemSession repoSession;

    public ChannelBuilder(RepositorySystem repoSystem, RepositorySystemSession repoSession) {
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
    }

    private Repository buildRestoringChannelRepository(Channel channel, Manifest manifest) throws IOException {
        final String channelDefinitionUrl = channel.getUrl();
        final ChannelDefinition curatedPolicies = new ChannelDefinitionParser().parsePolicyFile(new URL(channelDefinitionUrl));
        final List<RemoteRepository> repositories = toRepositories(channel.getName(), curatedPolicies.getRepositoryUrls());
        final DefaultResolver resolver = new DefaultResolver(repositories, repoSystem, repoSession);
        return new RestoringMavenRepository(resolver, manifest);
    }

    public Repository buildRestoringChannelRepository(List<Channel> channels, Manifest manifest) throws IOException {
        List<Repository> repos = new ArrayList<>();
        for (Channel channel : channels) {
            repos.add(buildRestoringChannelRepository(channel, manifest));
        }

        if (repos.size() == 1) {
            return repos.get(0);
        }

        final CombinedMavenRepository combinedMavenRepository = new CombinedMavenRepository(repos.toArray(new Repository[]{}));

        return combinedMavenRepository;
    }

    public Repository buildChannelRepository(Channel channel) throws IOException {
        final String channelDefinitionUrl = channel.getUrl();
        final ChannelDefinition curatedPolicies = new ChannelDefinitionParser().parsePolicyFile(new URL(channelDefinitionUrl));
        final List<RemoteRepository> repositories = toRepositories(channel.getName(), curatedPolicies.getRepositoryUrls());
        final DefaultResolver resolver = new DefaultResolver(repositories, repoSystem, repoSession);
        return new CuratedMavenRepository(resolver, curatedPolicies.getChannelRules());
    }

    public Repository buildChannelRepository(List<Channel> channels) throws IOException {
        List<Repository> repos = new ArrayList<>();
        for (Channel channel : channels) {
            repos.add(buildChannelRepository(channel));
        }

        if (repos.size() == 1) {
            return repos.get(0);
        }

        final CombinedMavenRepository combinedMavenRepository = new CombinedMavenRepository(repos.toArray(new Repository[]{}));

        return combinedMavenRepository;
    }

    private List<RemoteRepository> toRepositories(String channel, List<String> urls) {
        List<RemoteRepository> list = new ArrayList<>();
        if (urls == null) {
            return list;
        }

        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            RemoteRepository aDefault = new RemoteRepository.Builder(channel+i, "default", url).build();
            list.add(aDefault);
        }
        return list;
    }
}
