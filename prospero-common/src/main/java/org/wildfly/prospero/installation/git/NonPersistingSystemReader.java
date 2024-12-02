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

package org.wildfly.prospero.installation.git;

import java.io.IOException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

/**
 * A jgit system reader that uses default system/user/git configurations and does not persist them.
 * <p>
 * This avoids jgit having to create configuration files in e.g. user home. We use a limited scope of git and should
 * be self-contained.
 */
class NonPersistingSystemReader extends SystemReader {
    private final SystemReader systemReader;

    public NonPersistingSystemReader(SystemReader systemReader) {
        this.systemReader = systemReader;
    }

    @Override
    public String getHostname() {
        return systemReader.getHostname();
    }

    @Override
    public String getenv(String s) {
        return systemReader.getenv(s);
    }

    @Override
    public String getProperty(String s) {
        return systemReader.getProperty(s);
    }

    @Override
    public StoredConfig getUserConfig() throws ConfigInvalidException, IOException {
        return new NonPersistStoredConfig();
    }

    @Override
    public StoredConfig getJGitConfig() throws ConfigInvalidException, IOException {
        return new NonPersistStoredConfig();
    }

    @Override
    public StoredConfig getSystemConfig() throws ConfigInvalidException, IOException {
        return new NonPersistStoredConfig();
    }

    @Override
    public FileBasedConfig openUserConfig(Config config, FS fs) {
        return new EmptyFileBasedConfig(config, fs);
    }

    @Override
    public FileBasedConfig openSystemConfig(Config config, FS fs) {
        return new EmptyFileBasedConfig(config, fs);
    }

    @Override
    public FileBasedConfig openJGitConfig(Config config, FS fs) {
        return new EmptyFileBasedConfig(config, fs);
    }

    @Override
    public long getCurrentTime() {
        return systemReader.getCurrentTime();
    }

    @Override
    public int getTimezone(long l) {
        return systemReader.getTimezone(l);
    }

    private static class NonPersistStoredConfig extends StoredConfig {
        @Override
        public void load() throws IOException, ConfigInvalidException {
            clear();
        }

        @Override
        public void save() throws IOException {
            // do nothing
        }
    }

    private static class EmptyFileBasedConfig extends FileBasedConfig {
        public EmptyFileBasedConfig(Config config, FS fs) {
            super(config, null, fs);
        }

        @Override
        public void load() throws IOException, ConfigInvalidException {
            this.clear();
        }

        @Override
        public void save() throws IOException {
            // do nothing
        }
    }
}
