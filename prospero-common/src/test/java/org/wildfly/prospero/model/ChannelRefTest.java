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
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

public class ChannelRefTest {

    @Test
    public void fromString() throws MalformedURLException {
        ChannelRef channelRef = ChannelRef.fromString("file:/test");
        Assert.assertNull(channelRef.getGav());
        Assert.assertEquals(new URL("file:/test").toExternalForm(), channelRef.getUrl());

        ChannelRef.fromString("file:///test");
        Assert.assertNull(channelRef.getGav());
        Assert.assertEquals(new URL("file:/test").toExternalForm(), channelRef.getUrl());

        channelRef = ChannelRef.fromString("g:a");
        Assert.assertEquals("g:a", channelRef.getGav());
        Assert.assertNull(channelRef.getUrl());

        channelRef = ChannelRef.fromString("g:a:v");
        Assert.assertEquals("g:a:v", channelRef.getGav());
        Assert.assertNull(channelRef.getUrl());

        channelRef = ChannelRef.fromString("a/path");
        Assert.assertNull(channelRef.getGav());
        String cwd = Paths.get("").toAbsolutePath().toString();
        Assert.assertEquals("file:" + cwd + "/a/path", channelRef.getUrl());
    }
}
