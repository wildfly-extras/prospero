package org.wildfly.prospero.galleon;

import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class FeaturePackLocationParserTest {

    @Mock
    private ChannelMavenArtifactRepositoryManager repoManager;
    private FeaturePackLocationParser parser;

    @Before
    public void setUp() throws Exception {
        parser = new FeaturePackLocationParser(repoManager);
    }

    @Test
    public void findLatestFeaturePackInPartialGav() throws Exception {
        when(repoManager.getLatestVersion(any())).thenReturn("26.1.0.Final");
        final FeaturePackLocation resolvedFpl = resolveFplVersion("org.wildfly:wildfly-ee-galleon-pack");
        assertEquals("26.1.0.Final", resolvedFpl.getBuild());
        assertEquals("org.wildfly:wildfly-ee-galleon-pack::zip", resolvedFpl.getProducerName());
    }

    @Test
    public void useProvidedGavWhenUsedFull() throws Exception {
        final FeaturePackLocation resolvedFpl = resolveFplVersion("org.wildfly:wildfly-ee-galleon-pack:26.0.0.Final");
        assertEquals("26.0.0.Final", resolvedFpl.getBuild());
        assertEquals("org.wildfly:wildfly-ee-galleon-pack::zip", resolvedFpl.getProducerName());
    }

    @Test
    public void useUniverseIfProvided() throws Exception {
        assertEquals(null, resolveFplVersion("wildfly@maven(community-universe):current").getBuild());
        assertEquals("current", resolveFplVersion("wildfly@maven(community-universe):current").getChannelName());
    }

    private FeaturePackLocation resolveFplVersion(String fplText) throws MavenUniverseException {
        return parser.resolveFpl(fplText);
    }
}