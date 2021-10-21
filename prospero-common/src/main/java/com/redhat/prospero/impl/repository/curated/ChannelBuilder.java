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
    private Manifest restoringManifest;
    private List<Channel> channels;

    public ChannelBuilder(RepositorySystem repoSystem, RepositorySystemSession repoSession) {
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
    }

    public Repository build() throws IOException {
        if (channels == null) {
            throw new IllegalStateException("Cannot build channel repository without channels");
        }

        List<Repository> repos = new ArrayList<>();
        for (Channel channel : channels) {
            repos.add(buildChannelRepository2(channel));
        }

        if (repos.size() == 1) {
            return repos.get(0);
        }

        final CombinedMavenRepository combinedMavenRepository = new CombinedMavenRepository(repos.toArray(new Repository[]{}));

        return combinedMavenRepository;
    }

    private Repository buildChannelRepository2(Channel channel) throws IOException {
        final String channelDefinitionUrl = channel.getUrl();
        final ChannelDefinition curatedPolicies = new ChannelDefinitionParser().parsePolicyFile(new URL(channelDefinitionUrl));
        final List<RemoteRepository> repositories = toRepositories(channel.getName(), curatedPolicies.getRepositoryUrls());
        final DefaultResolver resolver = new DefaultResolver(repositories, repoSystem, repoSession);
        if (restoringManifest == null) {
            return new CuratedMavenRepository(resolver, curatedPolicies.getChannelRules());
        } else {
            return new RestoringMavenRepository(resolver, restoringManifest);
        }
    }

    public ChannelBuilder setRestoringManifest(Manifest restoringManifest) {
        this.restoringManifest = restoringManifest;
        return this;
    }

    public ChannelBuilder setChannels(List<Channel> channels) {
        this.channels = channels;
        return this;
    }

    public Repository buildChannelRepository(Channel channel) throws IOException {
        final String channelDefinitionUrl = channel.getUrl();
        final ChannelDefinition curatedPolicies = new ChannelDefinitionParser().parsePolicyFile(new URL(channelDefinitionUrl));
        final List<RemoteRepository> repositories = toRepositories(channel.getName(), curatedPolicies.getRepositoryUrls());
        final DefaultResolver resolver = new DefaultResolver(repositories, repoSystem, repoSession);
        return new CuratedMavenRepository(resolver, curatedPolicies.getChannelRules());
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
