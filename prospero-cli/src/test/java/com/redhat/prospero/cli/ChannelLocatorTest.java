/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.api.Server;
import com.redhat.prospero.cli.actions.Installation;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ChannelLocatorTest {

   private static final String EAP_DIR = "target/server-eap";
   private static final Path EAP_PATH = Paths.get(EAP_DIR).toAbsolutePath();
   private final Installation installation = new Installation(EAP_PATH);

   @Test
   public void findLatestEapChannelDefinition() throws Exception {
      final List<ChannelRef> channelRefs = Server.EAP.getChannelRefs();

      final String fpl = Server.EAP.getFpl();
      installation.provision(fpl, channelRefs);
   }

   @Test
   public void findLatestWildflyChannelDefinition() throws Exception {
      final List<ChannelRef> channelRefs = Server.WILFDFLY.getChannelRefs();

      final String fpl = Server.WILFDFLY.getFpl();
      installation.provision(fpl, channelRefs);
   }
}
