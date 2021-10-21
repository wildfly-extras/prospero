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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        Repository repository = builder.setChannels(Arrays.asList(channel)).build();

        assertTrue(repository instanceof CuratedMavenRepository);
    }

    @Test
    public void curatedRepository_hasResolverWithRepositoryUrl() throws Exception {
        final Path testRepoDir = Files.createTempDirectory("test-repo");
        final Path policyFile = testRepoDir.resolve("policy.json");
        mockArtifact(testRepoDir, "foo:bar:1.1");

        try(FileWriter fw = new FileWriter(policyFile.toFile())) {
            fw.write("{\"repositories\":[{\"url\":\"" + testRepoDir.toFile().toURI() + "\"}]}");
        }
        final Channel channel = new Channel("test", policyFile.toFile().toURI().toString());

        Repository repository = builder.setChannels(Arrays.asList(channel)).build();

        final File resolved = repository.resolve(new DefaultArtifact("foo:bar:1.1"));
        assertNotNull(resolved);
    }

    @Test
    public void twoChannels_resolveFilesFromBothRepositories() throws Exception {
        final Path testRepoDir1 = mockRepository();
        final Path testRepoDir2 = mockRepository();

        mockArtifact(testRepoDir1, "foo:bar:1.1");
        mockArtifact(testRepoDir2, "foo2:bar2:1.1");

        List<Channel> channels = new ArrayList<>();
        channels.add(new Channel("test1", testRepoDir1.resolve("policy.json").toFile().toURI().toString()));
        channels.add(new Channel("test2", testRepoDir2.resolve("policy.json").toFile().toURI().toString()));

        Repository repository = builder.setChannels(channels).build();

        assertNotNull(repository.resolve(new DefaultArtifact("foo:bar:1.1")));
        assertNotNull(repository.resolve(new DefaultArtifact("foo2:bar2:1.1")));
    }

    private Path mockRepository() throws IOException {
        final Path testRepoDir1 = Files.createTempDirectory("test-repo");
        final Path policyFile1 = testRepoDir1.resolve("policy.json");
        try (FileWriter fw = new FileWriter(policyFile1.toFile())) {
            fw.write("{\"repositories\":[{\"url\":\"" + testRepoDir1.toFile().toURI() + "\"}]}");
        }
        return testRepoDir1;
    }

    private void mockArtifact(Path testRepoDir, String gav) throws IOException {
        final String[] parts = gav.split(":");
        String group =  parts[0];
        String artifact = parts[1];
        String version = parts[2];
        final Path mockArtefact = testRepoDir.resolve(group.replace('.','/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar");
        Files.createDirectories(mockArtefact.getParent());
        Files.createFile(mockArtefact);
    }

    @Test
    public void multipleChannels_resultInCombinedMavenRepository() throws Exception {

    }
}