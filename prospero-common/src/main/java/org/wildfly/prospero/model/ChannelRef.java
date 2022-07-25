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

package org.wildfly.prospero.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChannelRef {

    private final String url;

    private final String gav;

    @JsonCreator
    public ChannelRef(@JsonProperty(value = "gav") String gav, @JsonProperty(value = "fileUrl") String fileUrl) {
        this.gav = gav;
        this.url = fileUrl;
    }

    public ChannelRef(ChannelRef other) {
        if (other.getGav() != null && !other.getGav().isEmpty()) {
            this.gav = other.getGav();
            this.url = null;
        } else {
            this.gav = null;
            this.url = other.getUrl();
        }
    }

    public String getUrl() {
        return url;
    }

    public String getGav() {
        return gav;
    }

    @JsonIgnore
    public String getGavOrUrlString() {
        if (StringUtils.isNotBlank(gav)) {
            return gav;
        } else {
            return url;
        }
    }

    @Override
    public String toString() {
        return "Channel{" + "gav='" + gav + '\'' + ", url='" + url + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelRef that = (ChannelRef) o;
        return Objects.equals(url, that.url) && Objects.equals(gav, that.gav);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, gav);
    }


    public static ChannelRef fromString(String urlGavOrPath) {
        try {
            URL url = new URL(urlGavOrPath);
            return new ChannelRef(null, url.toExternalForm());
        } catch (MalformedURLException e) {
            if (isValidCoordinate(urlGavOrPath)) {
                return new ChannelRef(urlGavOrPath, null);
            } else {
                // assume the string is a path
                try {
                    return new ChannelRef(null,
                            Paths.get(urlGavOrPath).toAbsolutePath().toUri().toURL().toExternalForm());
                } catch (MalformedURLException e2) {
                    throw new IllegalArgumentException("Can't convert path to URL", e2);
                }
            }
        }
    }

    private static boolean isValidCoordinate(String gav) {
        String[] parts = gav.split(":");
        return (parts.length == 3 // GAV
                && StringUtils.isNotBlank(parts[0])
                && StringUtils.isNotBlank(parts[1])
                && StringUtils.isNotBlank(parts[2]))
                ||
                (parts.length == 2 // GA
                && StringUtils.isNotBlank(parts[0])
                && StringUtils.isNotBlank(parts[1]));
    }
}
