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

package com.redhat.prospero.model;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class RepositoryRef {

    private String id;
    private String url;

    public RepositoryRef() {

    }

    public RepositoryRef(String id, String url) {
        this.id = id;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public static void writeRepositories(List<RepositoryRef> repositories, File repositoriesFile) throws IOException {
        new ObjectMapper(new YAMLFactory()).writeValue(repositoriesFile, repositories);
    }

    public static List<RepositoryRef> readRepositories(Path path) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, RepositoryRef.class);
        final List<RepositoryRef> repositories = objectMapper.readValue(path.toUri().toURL(), type);

        return repositories;
    }

    public RemoteRepository toRemoteRepository() {
        return new RemoteRepository.Builder(id, "default", url).build();
    }
}
