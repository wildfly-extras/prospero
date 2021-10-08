/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.redhat.prospero.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

/**
 * @author jdenise
 */
public class MavenFallback {

    private static class RequiredRepository {

        private final String id;
        private final String type;
        private final String url;
        private final RepositoryPolicy releasePolicy;
        private final RepositoryPolicy snapshotPolicy;

        RequiredRepository(String id, String type, String url, RepositoryPolicy releasePolicy, RepositoryPolicy snapshotPolicy) {
            this.id = id;
            this.type = type;
            this.url = url;
            this.releasePolicy = releasePolicy;
            this.snapshotPolicy = snapshotPolicy;
        }
    }

    public static final String GA_REPO_URL = "https://maven.repository.redhat.com/ga/";
    public static final String NEXUS_REPO_URL = "https://repository.jboss.org/nexus/content/groups/public/";
    private static final String DEFAULT_REPOSITORY_TYPE = "default";

    private static final Map<String, RequiredRepository> REQUIRED_REPOSITORIES = new HashMap<>();

    static {
        REQUIRED_REPOSITORIES.put(GA_REPO_URL, new RequiredRepository("jboss-ga-repository", DEFAULT_REPOSITORY_TYPE, GA_REPO_URL,
                new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_WARN),
                new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL)));
        REQUIRED_REPOSITORIES.put(NEXUS_REPO_URL, new RequiredRepository("jboss-public-repository", DEFAULT_REPOSITORY_TYPE, NEXUS_REPO_URL,
                new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_WARN),
                new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL)));
        REQUIRED_REPOSITORIES.put("https://repo1.maven.org/maven2", new RequiredRepository("repo1", DEFAULT_REPOSITORY_TYPE, NEXUS_REPO_URL,
                new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_WARN),
                new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL)));
    }

    public static List<RemoteRepository> buildRepositories() throws RuntimeException {
        List<RemoteRepository> repositories = new ArrayList<>();
        for (Map.Entry<String, RequiredRepository> entry : REQUIRED_REPOSITORIES.entrySet()) {
            RequiredRepository repo = entry.getValue();
            RemoteRepository.Builder builder = new RemoteRepository.Builder(repo.id, repo.type, repo.url);
            builder.setReleasePolicy(repo.releasePolicy);
            builder.setSnapshotPolicy(repo.snapshotPolicy);
            repositories.add(builder.build());
        }
        return repositories;
    }
}
