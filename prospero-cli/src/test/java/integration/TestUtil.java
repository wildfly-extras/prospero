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

package integration;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.model.ProvisioningConfig;
import org.wildfly.prospero.model.RepositoryRef;

public class TestUtil {

    public static final Path MANIFEST_FILE_PATH = Paths.get(InstallationMetadata.METADATA_DIR, InstallationMetadata.MANIFEST_FILE_NAME);
    public static final Path CHANNELS_FILE_PATH = Paths.get(InstallationMetadata.METADATA_DIR, InstallationMetadata.PROSPERO_CONFIG_FILE_NAME);

    public static URL prepareChannelFileAsUrl(String channelDescriptor) throws IOException {
        final Path channelFile = Files.createTempFile("channels", "yaml");
        channelFile.toFile().deleteOnExit();

        final Path path = prepareChannelFileAsUrl(channelFile, channelDescriptor);
        return path.toUri().toURL();
    }

    public static Path prepareChannelFile(String channelDescriptor) throws IOException {
        final Path channelFile = Files.createTempFile("channels", "yaml");
        channelFile.toFile().deleteOnExit();

        return prepareChannelFileAsUrl(channelFile, channelDescriptor);
    }

    public static Path prepareChannelFileAsUrl(Path channelFile, String... channelDescriptor) throws IOException {
        List<URL> channelUrls = Arrays.stream(channelDescriptor).map(d->TestUtil.class.getClassLoader().getResource(d)).collect(Collectors.toList());
        List<ChannelRef> channels = new ArrayList<>();
        List<RepositoryRef> repositories = WfCoreTestBase.defaultRemoteRepositories().stream()
                .map(r->new RepositoryRef(r.getId(), r.getUrl())).collect(Collectors.toList());
        for (int i=0; i<channelUrls.size(); i++) {
            channels.add(new ChannelRef(null, channelUrls.get(i).toString()));
        }
        new ProvisioningConfig(channels, repositories).writeConfig(channelFile.toFile());

        return channelFile;
    }
}
