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

package org.wildfly.prospero.spi.internal;

import org.wildfly.installationmanager.spi.OsShell;

import java.nio.file.Path;

/**
 * Service provider interface to expose CLI capabilities to API layer.
 */
public interface CliProvider {
    /**
     * returns the script name used to run Prospero. The returned script is OS-specific.
     * @param shell
     * @return
     */
    String getScriptName(OsShell shell);

    /**
     * generates command used to apply an update candidate in {@code candidatePath} into {@code installationPath}
     *
     * @param installationPath
     * @param candidatePath
     * @param noConflictsOnly - whether to append the no-conflicts-only flag
     * @return
     */
    String getApplyUpdateCommand(Path installationPath, Path candidatePath, boolean noConflictsOnly);

    /**
     * generates command used to apply a revert candidate in {@code candidatePath} into {@code installationPath}
     *
     * @param installationPath
     * @param candidatePath
     * @param noConflictsOnly - whether to append the no-conflicts-only flag
     * @return
     */
    String getApplyRevertCommand(Path installationPath, Path candidatePath, boolean noConflictsOnly);
}
