package org.wildfly.prospero.cli.commands.options;

import org.junit.Test;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class LocalRepoOptionsTest {

    @Test
    public void defaultLocalPathIfNoOptionsSpecified() throws Exception {
        assertEquals(Optional.of(MavenSessionManager.LOCAL_MAVEN_REPO), LocalRepoOptions.getLocalRepo(null));
    }

    @Test
    public void emptyLocalPathIfNoLocalCacheSpecified() throws Exception {
        final LocalRepoOptions localRepoParam = new LocalRepoOptions();
        localRepoParam.noLocalCache = true;

        assertEquals(Optional.empty(), LocalRepoOptions.getLocalRepo(localRepoParam));
    }

    @Test
    public void customLocalPathIfLocalRepoSpecified() throws Exception {
        final LocalRepoOptions localRepoParam = new LocalRepoOptions();
        localRepoParam.localRepo = Paths.get("test");

        assertEquals(Optional.of(Paths.get("test")), LocalRepoOptions.getLocalRepo(localRepoParam));
    }

}