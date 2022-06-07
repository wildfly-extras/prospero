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

package org.wildfly.prospero.cli;

public class Messages {
    public static String unexpectedPackageInSelfUpdate(String path) {
        return String.format("Unable to perform self-update - folder [%s] contains unexpected feature packs.", path);
    }

    public static String unableToLocateInstallation() {
        return "Unable to locate the installation folder to perform self-update";
    }

    public static String unableToParseSelfUpdateData() {
        return "Unable to perform self-update - unable to determine installed feature packs.";
    }

    public static String offlineModeRequiresLocalRepo() {
        return "Using offline mode requires a local-repo parameter present.";
    }
}
