package integration;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.actions.Installation;
import com.redhat.prospero.actions.Update;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.wfchannel.WfChannelMavenResolverFactory;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.Producer;
import org.jboss.galleon.universe.UniverseResolver;
import org.junit.Ignore;
import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.Stream;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.junit.Assert.assertTrue;

@Ignore
public class ChannelUpdaterTest {

    private static final String OUTPUT_DIR = "target/server";
    private static final Path OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();

    private static final String EAP_DIR = "target/server-eap";
    private static final Path EAP_PATH = Paths.get(EAP_DIR).toAbsolutePath();

    @Test
    public void findLatestEap() throws Exception {
        final Path channelFile = Paths.get("/Users/spyrkob/workspaces/set/prospero/prospero/examples/eap/channels-eap74.json");
        final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelFile);
        final List<Channel> channels = channelRefs.stream().map(ref-> {
            try {
                return ChannelMapper.from(new URL(ref.getUrl()));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory();
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(channels, factory);


        FeaturePackLocation loc = FeaturePackLocation.fromString("eap@maven(org.jboss.universe:product-universe):current");
        final UniverseResolver universe = UniverseResolver.builder()
                .addArtifactResolver(repoManager)
                .build();
        final Producer<?> producer = universe.getUniverse(loc.getUniverse()).getProducer(loc.getProducerName());
        System.out.println(producer.getDefaultChannel().getLatestBuild(loc));
    }

    @Test
    public void installEap74() throws Exception {
        final URL channelFile = ChannelUpdaterTest.class.getResource("/channels/eap/channels-eap74.json");

        if (Files.exists(EAP_PATH)) {
            throw new ProvisioningException("Installation dir " + EAP_PATH + " already exists");
        }
        final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelFile);

        new Installation(EAP_PATH).provision("org.jboss.eap:wildfly-ee-galleon-pack", channelRefs);

        // verify installation with manifest file is present
        assertTrue(EAP_PATH.resolve(TestUtil.MANIFEST_FILE_PATH).toFile().exists());
    }

    @Test
    public void updateEAP() throws Exception {
        new Update(EAP_PATH, true).doUpdateAll();
    }

    @Test
    public void updateWfly() throws Exception {
        new Update(OUTPUT_PATH, true).doUpdateAll();
    }

    @Test
    public void eap74() throws Exception {
        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory();

        final URL wlflUrl = new URL("file:///Users/spyrkob/workspaces/set/prospero/prospero/examples/eap/wildfly-ee-galleon-pack-7.4.3.GA-redhat-SNAPSHOT-channel.yaml");
        Channel primaryChannel = ChannelMapper.from(wlflUrl);

        ChannelSession session = new ChannelSession(asList(primaryChannel), factory);

//        final MavenArtifact resolved = session.resolveLatestMavenArtifact("io.undertow", "undertow-core", "jar", null, null);
        final MavenArtifact resolved = session.resolveLatestMavenArtifact("org.eclipse", "yasson", "jar", null, "1.0.9.redhat-00001");
        System.out.println("Resolved " + resolved.getVersion());
    }

    @Test
    public void installWildfly() throws Exception {
        final URL channelFile = ChannelUpdaterTest.class.getResource("/channels/wfly/channels-wfly24.json");

        if (Files.exists(OUTPUT_PATH)) {
            throw new ProvisioningException("Installation dir " + OUTPUT_DIR + " already exists");
        }
        final List<ChannelRef> channelRefs = ChannelRef.readChannels(channelFile);

        new Installation(OUTPUT_PATH).provision("org.wildfly:wildfly-ee-galleon-pack:24.0.0.Final", channelRefs);

        // verify installation with manifest file is present
        assertTrue(OUTPUT_PATH.resolve(TestUtil.MANIFEST_FILE_PATH).toFile().exists());
    }

//    @Test
    public void testMe() throws Exception {

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory();

        final URL wlflUrl = new URL("file:///Users/spyrkob/workspaces/set/prospero/prospero/examples/wfly/wfly-24.yaml");
        Channel primaryChannel = ChannelMapper.from(wlflUrl);

        final URL undertowUrl = new URL("file:///Users/spyrkob/workspaces/set/prospero/prospero/examples/wfly/undertow.yaml");
        Channel compChannel = ChannelMapper.from(undertowUrl);

        ChannelSession session = new ChannelSession(asList(compChannel), factory);

        final MavenArtifact resolved = session.resolveLatestMavenArtifact("io.undertow", "undertow-core", "jar", null, null);

        System.out.println(resolved.getVersion());

        if (true) {
            return;
        }

        final Optional<Stream> first = primaryChannel.getStreams().stream().filter(s -> s.getArtifactId().equals("undertow-core")).findFirst();
        if (first.isPresent()) {
            primaryChannel.getStreams().remove(first.get());
            primaryChannel.getStreams().add(new Stream("io.undertow", "undertow-core", "2.2.9.Final", null));
        }

        System.out.println(ChannelMapper.toYaml(primaryChannel));
    }
}
