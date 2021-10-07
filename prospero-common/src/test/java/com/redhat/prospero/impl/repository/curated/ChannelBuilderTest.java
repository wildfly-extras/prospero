package com.redhat.prospero.impl.repository.curated;

import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.maven.MavenUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ChannelBuilderTest {

    RepositorySystem repositorySystem = MavenUtils.defaultRepositorySystem();
    private ChannelBuilder builder = new ChannelBuilder(repositorySystem, MavenUtils.getDefaultRepositorySystemSession(repositorySystem));

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
}