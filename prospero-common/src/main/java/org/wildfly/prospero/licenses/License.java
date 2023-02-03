/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.licenses;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

public class License {
    private final String name;
    private final String fpGav;
    private final String text;
    private final String title;

    @JsonCreator
    public License(@JsonProperty(value = "name") String name,
                   @JsonProperty(value = "fpGAV") String fpGav,
                   @JsonProperty(value = "title") String title,
                   @JsonProperty(value = "text") String text) {
        this.name = name;
        this.fpGav = fpGav;
        this.title = title.trim();
        this.text = text.trim();
    }

    public static List<License> readLicenses(URL url) throws IOException {
        final YAMLFactory yamlFactory = new YAMLFactory();
        final ObjectMapper objectMapper = new ObjectMapper(yamlFactory);
        return objectMapper.readValue(url, new TypeReference<List<License>>(){});
    }

    public static void writeLicenses(List<License> licenses, File targetFile) throws IOException {
        final YAMLFactory yamlFactory = new YAMLFactory();
        final ObjectMapper objectMapper = new ObjectMapper(yamlFactory);
        objectMapper.writeValue(targetFile, licenses);
    }

    public String getName() {
        return name;
    }

    public String getFpGav() {
        return fpGav;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        License license = (License) o;
        return Objects.equals(name, license.name) && Objects.equals(fpGav, license.fpGav) && Objects.equals(text, license.text) && Objects.equals(title, license.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fpGav, text, title);
    }
}
