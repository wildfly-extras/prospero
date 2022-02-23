package integration;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.actions.Installation;
import com.redhat.prospero.actions.Update;
import com.redhat.prospero.api.ProvisioningDefinition;
import com.redhat.prospero.cli.CliConsole;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.wfchannel.MavenSessionManager;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;

@Ignore
public class ChannelUpdaterTest {

    private static final String OUTPUT_DIR = "target/server";
    private static final Path OUTPUT_PATH = Paths.get(OUTPUT_DIR).toAbsolutePath();

    private static final String EAP_DIR = "target/server-eap";
    private static final Path EAP_PATH = Paths.get(EAP_DIR).toAbsolutePath();

    private MavenSessionManager mavenSessionManager = new MavenSessionManager();

    public ChannelUpdaterTest() throws Exception {
    }

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

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory(mavenSessionManager);
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

        final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
           .setFpl("org.jboss.eap:wildfly-ee-galleon-pack")
           .build();
        new Installation(EAP_PATH, mavenSessionManager, new CliConsole()).provision(provisioningDefinition);

        // verify installation with manifest file is present
        assertTrue(EAP_PATH.resolve(TestUtil.MANIFEST_FILE_PATH).toFile().exists());
    }

    @Test
    public void updateEAP() throws Exception {
        new Update(EAP_PATH, mavenSessionManager, new AcceptingConsole()).doUpdateAll();
    }

    @Test
    public void updateWfly() throws Exception {
        new Update(OUTPUT_PATH, mavenSessionManager, new AcceptingConsole()).doUpdateAll();
    }

    @Test
    public void eap74() throws Exception {
        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory(mavenSessionManager);

        final URL wlflUrl = new URL("file:///Users/spyrkob/workspaces/set/prospero/prospero/examples/eap/wildfly-ee-galleon-pack-7.4.3.GA-redhat-SNAPSHOT-channel.yaml");
        Channel primaryChannel = ChannelMapper.from(wlflUrl);

        ChannelSession session = new ChannelSession(asList(primaryChannel), factory);

        final MavenArtifact resolved = session.resolveLatestMavenArtifact("org.eclipse", "yasson", "jar", null, "1.0.9.redhat-00001");
        System.out.println("Resolved " + resolved.getVersion());
    }

    @Test
    public void installWildfly() throws Exception {

        if (Files.exists(OUTPUT_PATH)) {
            throw new ProvisioningException("Installation dir " + OUTPUT_DIR + " already exists");
        }

        final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
           .setFpl("org.wildfly:wildfly-ee-galleon-pack:24.0.0.Final")
           .build();
        new Installation(OUTPUT_PATH, mavenSessionManager, new CliConsole()).provision(provisioningDefinition);

        // verify installation with manifest file is present
        assertTrue(OUTPUT_PATH.resolve(TestUtil.MANIFEST_FILE_PATH).toFile().exists());
    }
}
