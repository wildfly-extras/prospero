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

package com.redhat.prospero.impl.repository.curated;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Resolver;
import com.redhat.prospero.impl.repository.MavenRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.jboss.galleon.universe.maven.MavenUniverseException;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

public class CuratedMavenRepository extends MavenRepository {

    private static final Logger log = LogManager.getLogger(CuratedMavenRepository.class);

    private final ChannelRules channelRules;

    public CuratedMavenRepository(Resolver resolver, ChannelRules channelRules) {
        super(resolver, false);
        this.channelRules = channelRules;
    }

    protected Version getHighestVersion(VersionRangeResult versionRangeResult, Artifact artifact) throws ArtifactNotFoundException {
        final String baseVersion = artifact.getVersion();
        final ChannelRules.Policy policy = getUpdatesPolicy(artifact);

        final Optional<Version> highestVersion = versionRangeResult.getVersions().stream()
                .filter(policy.getFilter(baseVersion))
                .max(Comparator.naturalOrder());

        return highestVersion.orElseThrow(()->{
            log.error("No versions found for {}:{}", artifact.getGroupId(), artifact.getArtifactId());
            return new ArtifactNotFoundException("");});
    }

    @Override
    public VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
        final VersionRangeResult versionRange = super.getVersionRange(artifact);

        final ChannelRules.Policy policy = getUpdatesPolicy(artifact);

        final VersionRangeResult filtered = new VersionRangeResult(versionRange.getRequest());
        filtered.setVersions(
                versionRange.getVersions().stream()
                        .filter(policy.getFilter(artifact.getVersion()))
                        .collect(Collectors.toList()));

        return filtered;
    }

    private ChannelRules.Policy getUpdatesPolicy(Artifact artifact) {
        String ga = artifact.getGroupId() + ":" + artifact.getArtifactId();
        final ChannelRules.Policy policy = channelRules.getPolicy(ga);
        return policy==null? ChannelRules.Policy.ANY:policy;
    }
}
