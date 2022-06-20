package org.wildfly.prospero.api;

import java.util.function.Supplier;

import org.eclipse.aether.repository.RemoteRepository;

/**
 * Enumeration defining well known Maven repositories.
 */
public enum WellKnownRepositories implements Supplier<RemoteRepository> {

    MRRC(new RemoteRepository.Builder("mrrc", "default", "https://maven.repository.redhat.com/ga/")
            .build()),

    CENTRAL(new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/")
            .build()),

    JBOSS_PUBLIC(new RemoteRepository.Builder("jboss-public", "default", "https://repository.jboss.org/nexus/content/groups/public/")
            .build());

    private final RemoteRepository repository;

    private WellKnownRepositories(RemoteRepository repository) {
        this.repository = repository;
    }

    @Override
    public RemoteRepository get() {
        return repository;
    }
}
