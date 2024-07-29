/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.api.exceptions;

import java.nio.file.Path;

public class ApplyCandidateException extends OperationException {

    private final boolean rollbackSuccessful;
    private final Path backupPath;

    public ApplyCandidateException(String msg, boolean rollbackSuccessful, Path backupPath, Throwable e) {
        super(msg, e);
        this.rollbackSuccessful = rollbackSuccessful;
        this.backupPath = backupPath;
    }

    public boolean isRollbackSuccessful() {
        return rollbackSuccessful;
    }

    public Path getBackupPath() {
        return backupPath;
    }
}
