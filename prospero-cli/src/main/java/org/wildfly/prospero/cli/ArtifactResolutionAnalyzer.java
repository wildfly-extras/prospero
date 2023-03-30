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

package org.wildfly.prospero.cli;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.prospero.api.ArtifactUtils;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class ArtifactResolutionAnalyzer {

    static class Result {
        private final String status;
        private final String coords;

        public Result(String coords, String status) {
            this.coords = coords;
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public String getCoords() {
            return coords;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "status='" + status + '\'' +
                    ", coords='" + coords + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Result result = (Result) o;
            return Objects.equals(status, result.status) && Objects.equals(coords, result.coords);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, coords);
        }
    }

    List<Result> analyze(ArtifactResolutionException ex) {
        if (ex.getMissingArtifacts().isEmpty()) {
            return Collections.emptyList();
        }

        final List<Result> res = new ArrayList<>();
        final List<ArtifactResult> results = getArtifactResults(ex);
        for (ArtifactCoordinate missingArtifact : ex.getMissingArtifacts()) {
            final String status;
            if (failedChecksum(getResult(missingArtifact, results))) {
                status = CliMessages.MESSAGES.checksumFailed();
            } else {
                status = CliMessages.MESSAGES.missing();
            }
            res.add(new Result(ArtifactUtils.printCoordinate(missingArtifact), status));
        }

        return res;
    }

    private static List<ArtifactResult> getArtifactResults(ArtifactResolutionException e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
            if (t instanceof org.eclipse.aether.resolution.ArtifactResolutionException) {
                return ((org.eclipse.aether.resolution.ArtifactResolutionException) t).getResults().stream()
                        .filter(r -> !r.isResolved())
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    private static ArtifactResult getResult(ArtifactCoordinate coord, List<ArtifactResult> results) {
        for (ArtifactResult res : results) {
            final Artifact artifact = res.getRequest().getArtifact();
            if (artifact.getGroupId().equals(coord.getGroupId()) && artifact.getArtifactId().equals(coord.getArtifactId())
                    && artifact.getVersion().equals(coord.getVersion()) && artifact.getExtension().equals(coord.getExtension())) {
                return res;
            }
        }
        return null;
    }

    private static boolean failedChecksum(ArtifactResult result) {
        if (result == null) {
            return false;
        }

        for (Exception e : result.getExceptions()) {
            if (e instanceof ArtifactTransferException && e.getCause() instanceof ChecksumFailureException) {
                return true;
            }
        }
        return false;
    }
}
