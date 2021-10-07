package com.redhat.prospero.impl.repository.curated;

import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Repository;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ChannelBuilderTest {

    RepositorySystem repositorySystem = defaultRepositorySystem();
    private ChannelBuilder builder = new ChannelBuilder(repositorySystem, defaultRepositorySystemSession(repositorySystem));

    @Test
    public void policyFile_createsCuratedRepository() throws Exception {
        final Path testRepoDir = Files.createTempDirectory("test-repo");
        final Path policyFile = testRepoDir.resolve("policy.json");
        try(FileWriter fw = new FileWriter(policyFile.toFile())) {
            fw.write("{}");
        }
        final Channel channel = new Channel("test", policyFile.toUri().toURL().toString());

        Repository repository = builder.buildChannelRepository(channel);

        assertTrue(repository instanceof CuratedMavenRepository);
    }

    @Test
    public void curatedRepository_hasResolverWithRepositoryUrl() throws Exception {
        final Path testRepoDir = Files.createTempDirectory("test-repo");
        final Path policyFile = testRepoDir.resolve("policy.json");
        final Path mockArtefact = testRepoDir.resolve("foo/bar/1.1/bar-1.1.jar");
        Files.createDirectories(mockArtefact.getParent());
        Files.createFile(mockArtefact);

        try(FileWriter fw = new FileWriter(policyFile.toFile())) {
            fw.write("{\"repositoryUrl\":\"" + testRepoDir.toFile().toURI() + "\"}");
        }
        final Channel channel = new Channel("test", policyFile.toFile().toURI().toString());

        Repository repository = builder.buildChannelRepository(channel);

        final File resolved = repository.resolve(new DefaultArtifact("foo:bar:1.1"));
        assertNotNull(resolved);
    }

    public static RepositorySystem defaultRepositorySystem() {
        /*
         * Aether's components implement org.eclipse.aether.spi.locator.Service to ease manual wiring and using the
         * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter
         * factories.
         */
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                System.out.println(String.format("Service creation failed for %s with implementation %s",
                        type, impl));
                exception.printStackTrace();
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    private static DefaultRepositorySystemSession defaultRepositorySystemSession(RepositorySystem system) {
        try {
            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

            org.eclipse.aether.repository.LocalRepository localRepo = new LocalRepository(Files.createTempDirectory("mvn-repo").toString());
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

            return session;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}