/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.prospero.galleon;

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.After;
import org.junit.Test;
import org.wildfly.channel.Repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MavenProxyHandlerTest {

    @After
    public void clearProperties() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("http.proxyUser");
        System.clearProperty("http.proxyPassword");

        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("https.proxyUser");
        System.clearProperty("https.proxyPassword");

        System.clearProperty("http.nonProxyHosts");
    }

    @Test
    public void noProxiesAddedIfSettingsAreNotPresent() throws Exception {
        final RemoteRepository result = MavenProxyHandler.addProxySettings(new Repository("test", "http://foo.bar"));

        assertNull(result.getProxy());
    }

    @Test
    public void addUnauthenticatedHttpProxy() throws Exception {
        System.setProperty("http.proxyHost", "http://proxy");
        System.setProperty("http.proxyPort", "8888");
        final RemoteRepository result = MavenProxyHandler.addProxySettings(new Repository("test", "http://foo.bar"));

        assertThat(result.getProxy())
                .hasFieldOrPropertyWithValue("host", "http://proxy")
                .hasFieldOrPropertyWithValue("port", 8888);

        assertNull(MavenProxyHandler.addProxySettings(new Repository("test", "https://foo.bar")).getProxy());
    }

    @Test
    public void addUnauthenticatedHttpsProxy() throws Exception {
        System.setProperty("https.proxyHost", "http://proxy");
        System.setProperty("https.proxyPort", "8888");
        final RemoteRepository result = MavenProxyHandler.addProxySettings(new Repository("test", "https://foo.bar"));

        assertThat(result.getProxy())
                .hasFieldOrPropertyWithValue("host", "http://proxy")
                .hasFieldOrPropertyWithValue("port", 8888);

        assertNull(MavenProxyHandler.addProxySettings(new Repository("test", "http://foo.bar")).getProxy());
    }

    @Test
    public void addAuthenticatedHttpsProxy() throws Exception {
        System.setProperty("https.proxyHost", "http://proxy");
        System.setProperty("https.proxyPort", "8888");
        System.setProperty("https.proxyUser", "test");
        System.setProperty("https.proxyPassword", "pwd");
        final RemoteRepository result = MavenProxyHandler.addProxySettings(new Repository("test", "https://foo.bar"));

        assertThat(result.getProxy())
                .hasFieldOrPropertyWithValue("host", "http://proxy")
                .hasFieldOrPropertyWithValue("port", 8888);
        assertNotNull(result.getProxy().getAuthentication());
    }

    @Test
    public void addAuthenticatedHttpProxy() throws Exception {
        System.setProperty("http.proxyHost", "http://proxy");
        System.setProperty("http.proxyPort", "8888");
        System.setProperty("http.proxyUser", "test");
        System.setProperty("http.proxyPassword", "pwd");
        final RemoteRepository result = MavenProxyHandler.addProxySettings(new Repository("test", "http://foo.bar"));

        assertThat(result.getProxy())
                .hasFieldOrPropertyWithValue("host", "http://proxy")
                .hasFieldOrPropertyWithValue("port", 8888);
        assertNotNull(result.getProxy().getAuthentication());
    }

    @Test
    public void dontAddAuthenticationIfPartiallyConfigured() throws Exception {
        System.setProperty("http.proxyHost", "http://proxy");
        System.setProperty("http.proxyPort", "8888");
        System.setProperty("http.proxyPassword", "pwd");
        final RemoteRepository result = MavenProxyHandler.addProxySettings(new Repository("test", "http://foo.bar"));

        assertThat(result.getProxy())
                .hasFieldOrPropertyWithValue("host", "http://proxy")
                .hasFieldOrPropertyWithValue("port", 8888);
        assertNull(result.getProxy().getAuthentication());
    }

    @Test
    public void defaultPortTo80IfNotPresent() throws Exception {
        System.setProperty("http.proxyHost", "http://proxy");
        final RemoteRepository result = MavenProxyHandler.addProxySettings(new Repository("test", "http://foo.bar"));

        assertThat(result.getProxy())
                .hasFieldOrPropertyWithValue("host", "http://proxy")
                .hasFieldOrPropertyWithValue("port", 80);
    }

    @Test
    public void skipProxyIfHostExcluded() throws Exception {
        System.setProperty("http.proxyHost", "http://proxy");
        System.setProperty("http.nonProxyHosts", "foo.bar");
        final RemoteRepository result = MavenProxyHandler.addProxySettings(new Repository("test", "http://foo.bar"));

        assertNull(result.getProxy());
    }

    @Test
    public void skipProxyIfRepositoryUrlInvalid() throws Exception {
        System.setProperty("http.proxyHost", "http://proxy");

        assertNull(MavenProxyHandler.addProxySettings(new Repository("test", "http:foo.bar")).getProxy());
        assertNull(MavenProxyHandler.addProxySettings(new Repository("test", "httpfoo.bar")).getProxy());
        assertNull(MavenProxyHandler.addProxySettings(new Repository("test", "")).getProxy());
    }

}