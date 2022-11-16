/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.actions;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import org.jboss.galleon.progresstracking.ProgressCallback;
import org.wildfly.prospero.api.ArtifactChange;

public interface Console {

    // installation
    void installationComplete();
    //   galleon progress

    ProgressCallback<?> getProgressCallback(String id);
    //   error handling - maybe not, use exceptions and log at the CLI handler


    // update
    void updatesFound(List<ArtifactChange> changes);

    boolean confirmUpdates();

    boolean confirm(String prompt, String accepted, String cancelled);

    void updatesComplete();

    default void println(String text) {
        getStdOut().println(text);
    }

    default void println(String text, String... args) {
        getStdOut().println(String.format(text, args));
    }

    default void error(String message, String... args) {
        getErrOut().println(String.format(message, args));
    }

    default PrintStream getStdOut() {
        return System.out;
    }

    default PrintStream getErrOut() {
        return System.err;
    }

    default InputStream getInput() {
        return System.in;
    }
}
