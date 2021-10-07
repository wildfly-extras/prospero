package com.redhat.prospero.api;

import com.redhat.prospero.impl.repository.DefaultResolver;
import com.redhat.prospero.impl.repository.MavenRepository;
import com.redhat.prospero.impl.repository.curated.CuratedMavenRepository;
import com.redhat.prospero.impl.repository.curated.ChannelDefinition;
import com.redhat.prospero.impl.repository.curated.ChannelDefinitionParser;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ChannelBuilderTest {

    @Test
    public void policyFile_createsCuratedRepository() throws Exception {
        final Path testRepoDir = Files.createTempDirectory("test-repo");
        final Path policyFile = testRepoDir.resolve("policy.json");
        try(FileWriter fw = new FileWriter(policyFile.toFile())) {
            fw.write("{}");
        }
        final Channel channel = new Channel("test", policyFile.toUri().toURL().toString());

        Repository repository = buildChannelRepository(channel);

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

        MavenRepository repository = (MavenRepository) buildChannelRepository(channel);

        final File resolved = repository.resolve(new DefaultArtifact("foo:bar:1.1"));
        assertNotNull(resolved);
    }

    private Repository buildChannelRepository(Channel channel) throws IOException {
        final String channelDefinitionUrl = channel.getUrl();
        final ChannelDefinition curatedPolicies = new ChannelDefinitionParser().parsePolicyFile(new URL(channelDefinitionUrl));
        final List<RemoteRepository> repositories = Arrays.asList(newRepository(channel.getName(), curatedPolicies.getRepositoryUrl()));
        final DefaultResolver resolver = new DefaultResolver(repositories, newRepositorySystem());
        return new CuratedMavenRepository(resolver, curatedPolicies.getChannelRules());
    }

    private RemoteRepository newRepository(String channel, String url) {
        return new RemoteRepository.Builder(channel, "default", url).build();
    }

    public static RepositorySystem newRepositorySystem() {
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
}