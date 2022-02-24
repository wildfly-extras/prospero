/**
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

package com.redhat.prospero.cli;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.redhat.prospero.actions.Update;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UpdateArgsTest {

    @Mock
    private Update update;

    @Mock
    private CliMain.ActionFactory actionFactory;

    @Test
    public void errorIfTargetPathNotPresent() throws Exception {
        try {
            Map<String, String> args = new HashMap<>();
            new UpdateArgs(actionFactory).handleArgs(args);
            fail("Should have failed");
        } catch (ArgumentParsingException e) {
            assertEquals("Target dir argument (--dir) need to be set on update command", e.getMessage());
        }
    }

    @Test
    public void callUpdate() throws Exception {
        when(actionFactory.update(any(), any())).thenReturn(update);

        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        new UpdateArgs(actionFactory).handleArgs(args);

        Mockito.verify(actionFactory).update(eq(Paths.get("test").toAbsolutePath()), any());
        Mockito.verify(update).doUpdateAll();
    }
}
