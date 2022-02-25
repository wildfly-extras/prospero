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

import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.api.SavedState;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HistoryCommand implements Command {

    private final CliMain.ActionFactory actionFactory;
    private final Console console;

    public HistoryCommand(CliMain.ActionFactory actionFactory, Console console) {
        this.actionFactory = actionFactory;
        this.console = console;
    }

    @Override
    public String getOperationName() {
        return "history";
    }

    @Override
    public Set<String> getSupportedArguments() {
        return new HashSet<>(Arrays.asList(CliMain.TARGET_PATH_ARG, CliMain.REVISION));
    }

    @Override
    public void execute(Map<String, String> parsedArgs) throws ArgumentParsingException, MetadataException {
        String dir = parsedArgs.get(CliMain.TARGET_PATH_ARG);
        String rev = parsedArgs.get(CliMain.REVISION);
        if (dir == null || dir.isEmpty()) {
            throw new ArgumentParsingException("Target dir argument (--%s) need to be set on history command", CliMain.TARGET_PATH_ARG);
        }

        if (rev == null || rev.isEmpty()) {
            final List<SavedState> revisions = actionFactory.history(Paths.get(dir).toAbsolutePath()).getRevisions();
            for (SavedState savedState : revisions) {
                console.println(savedState.shortDescription());
            }
        } else {
            final List<ArtifactChange> changes = actionFactory.history(Paths.get(dir).toAbsolutePath()).compare(new SavedState(rev));
            if (changes.isEmpty()) {
                console.println("No changes found");
            } else {
                changes.forEach((c-> console.println(c.toString())));
            }
        }
    }
}
