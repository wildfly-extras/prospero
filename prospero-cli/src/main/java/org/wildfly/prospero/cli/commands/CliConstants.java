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

package org.wildfly.prospero.cli.commands;

/**
 * CLI string constants
 */
public final class CliConstants {

    private CliConstants() {
    }

    /**
     * Command names
     */
    public static final class Commands {

        private Commands() {
        }

        public static final String MAIN_COMMAND = "prospero";
        public static final String INSTALL = "install";
        public static final String UPDATE = "update";
        public static final String BUILD_UPDATE = "build-update";
        public static final String HISTORY = "history";
        public static final String REVERT = "revert";
        public static final String CHANNEL = "channel";

        public static final String LIST = "list";
        public static final String ADD = "add";
        public static final String REMOVE = "remove";

        public static final String CUSTOMIZATION_INIT_CHANNEL = "init";
        public static final String CUSTOMIZATION_INITIALIZE_CHANNEL = "initialize";
        public static final String CUSTOMIZATION_PROMOTE = "promote";

        public static final String CLONE = "clone";
        public static final String RESTORE = "restore";
        public static final String EXPORT = "export";
    }

    // Parameter and option labels:

    public static final String CHANNEL_REFERENCE = "<channel-reference>";
    public static final String CHANNEL_MANIFEST_REFERENCE = "<manifest-reference>";
    public static final String FEATURE_PACK_REFERENCE = "<feature-pack-reference>";
    public static final String PATH = "<path>";
    public static final String REPO_URL = "<repo-url>";

    // Option names:

    public static final String CHANNEL_MANIFEST = "--manifest";
    public static final String CHANNEL = "--channel";
    public static final String CHANNELS = "--channels";
    public static final String REPOSITORIES = "--repositories";
    public static final String DEFINITION = "--definition";
    public static final String DIR = "--dir";
    public static final String DRY_RUN = "--dry-run";
    public static final String FPL = "--fpl";
    public static final String H = "-h";
    public static final String HELP = "--help";
    public static final String LOCAL_CACHE = "--local-cache";
    public static final String NO_LOCAL_MAVEN_CACHE = "--no-resolve-local-cache";
    public static final String OFFLINE = "--offline";
    public static final String REVISION = "--revision";
    public static final String SELF = "--self";
    public static final String TARGET_DIR = "--target-dir";
    public static final String V = "-v";
    public static final String VERSION = "--version";
    public static final String Y = "-y";
    public static final String YES = "--yes";

    public static final String CHANNEL_NAME = "--channel-name";
    public static final String CUSTOMIZATION_REPOSITORY_URL = "--repository-url";
    public static final String CUSTOMIZATION_ARCHIVE = "--archive";
    public static final String ARG_PATH = "--path";
}
