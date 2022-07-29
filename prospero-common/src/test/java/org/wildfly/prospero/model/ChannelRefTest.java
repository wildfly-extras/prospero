/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.model;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChannelRefTest {

    @Test
    public void fromString() throws MalformedURLException {
        ChannelRef channelRef = ChannelRef.fromString("file:/test");
        assertNull(channelRef.getGav());
        assertEquals(new URL("file:/test").toExternalForm(), channelRef.getUrl());

        ChannelRef.fromString("file:///test");
        assertNull(channelRef.getGav());
        assertEquals(new URL("file:/test").toExternalForm(), channelRef.getUrl());

        channelRef = ChannelRef.fromString("g:a");
        assertEquals("g:a", channelRef.getGav());
        assertNull(channelRef.getUrl());

        channelRef = ChannelRef.fromString("g:a:v");
        assertEquals("g:a:v", channelRef.getGav());
        assertNull(channelRef.getUrl());

        channelRef = ChannelRef.fromString("a/path");
        assertNull(channelRef.getGav());
        Path cwd = Paths.get("a/path").toAbsolutePath();
        assertEquals(cwd.toUri().toURL().toString(), channelRef.getUrl());
        assertTrue(channelRef.getUrl().startsWith("file:"));
    }
}
