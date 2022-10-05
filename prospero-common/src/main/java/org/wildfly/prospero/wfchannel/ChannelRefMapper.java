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

package org.wildfly.prospero.wfchannel;

import org.wildfly.channel.Channel;
import org.wildfly.channel.InvalidChannelException;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.model.ChannelRef;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChannelRefMapper {
    private VersionResolverFactory factory;

    public ChannelRefMapper(VersionResolverFactory factory) {
        this.factory = factory;
    }

    public List<Channel> mapToChannel(List<ChannelRef> channelRefs) throws OperationException {
        try {
            return factory.resolveChannels(channelRefs.stream().map(ChannelRef::toChannelCoordinate).collect(Collectors.toList()));
        } catch (MalformedURLException e) {
            throw Messages.MESSAGES.unableToResolveChannelConfiguration(e);
        } catch (InvalidChannelException e) {
            final String refs = channelRefs.stream().map(ChannelRef::getGavOrUrlString).collect(Collectors.joining("; "));
            throw Messages.MESSAGES.unableToParseChannel(refs, e);
        } catch (UnresolvedMavenArtifactException e) {
            // TODO: improve the errors coming from wildfly-channel so we don't need to do this parsing
            final List<String> ga = parseFailedGa(e.getMessage());
            if (!ga.isEmpty()) {
                throw Messages.MESSAGES.artifactNotFound(ga.get(0), ga.get(1), e);
            } else {
                throw new OperationException(e.getMessage(), e);
            }
        }
    }

    private List<String> parseFailedGa(String msg) {
        final Pattern pattern = Pattern.compile("Unable to resolve the latest version of channel (\\S+):(\\S+)");
        final Matcher matcher = pattern.matcher(msg);
        if (matcher.matches() && matcher.groupCount() == 2) {
            return List.of(matcher.group(1), matcher.group(2));
        } else {
            return Collections.emptyList();
        }
    }
}
