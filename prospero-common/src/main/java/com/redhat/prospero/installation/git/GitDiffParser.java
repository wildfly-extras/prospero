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

package com.redhat.prospero.installation.git;

import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.model.ManifestXmlSupport;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

/*
 * Assumes artifact per line in XML descriptor
 */
class GitDiffParser {

    private final Git git;

    GitDiffParser(Git git) {
        this.git = git;
    }

    List<ArtifactChange> getChanges(String rev) throws IOException {
        final CanonicalTreeParser oldTree = getTree(git, rev);
        final CanonicalTreeParser newTree = getTree(git, "HEAD");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try( DiffFormatter formatter = new DiffFormatter( outputStream ) ) {
            formatter.setRepository( git.getRepository() );
            formatter.format( oldTree, newTree );
        }

        String diff = outputStream.toString();
        final StringTokenizer st = new StringTokenizer(diff, System.lineSeparator());
        HashMap<String, Pair<Artifact, Artifact>> updates = new HashMap<>();
        while (st.hasMoreElements()) {
            String line = st.nextElement().toString();
            if (line.startsWith("-") && !line.startsWith("---")) {
                final Artifact artifact = ManifestXmlSupport.parseLine(line);
                final String key = artifact.getGroupId() + ":" + artifact.getGroupId() + ":" + artifact.getClassifier() + ":" + artifact.getExtension();
                if (updates.containsKey(key)) {
                    updates.put(key, Pair.of(artifact, updates.get(key).getRight()));
                } else {
                    updates.put(key, Pair.of(artifact, null));
                }
            }
            if (line.startsWith("+") && !line.startsWith("+++")) {
                final Artifact artifact = ManifestXmlSupport.parseLine(line);
                final String key = artifact.getGroupId() + ":" + artifact.getGroupId() + ":" + artifact.getClassifier() + ":" + artifact.getExtension();
                if (updates.containsKey(key)) {
                    updates.put(key, Pair.of(updates.get(key).getLeft(), artifact));
                } else {
                    updates.put(key, Pair.of(null, artifact));
                }
            }
        }

        final ArrayList<ArtifactChange> res = new ArrayList<>();
        for (String key : updates.keySet()) {
            final Pair<Artifact, Artifact> change = updates.get(key);
            res.add(new ArtifactChange(change.getLeft(), change.getRight()));
        }

        return res;
    }

    private CanonicalTreeParser getTree(Git git, String rev) throws IOException {
        try( RevWalk walk = new RevWalk( git.getRepository() ) ) {
            RevCommit commit = walk.parseCommit(git.getRepository().resolve(rev));
            ObjectId treeId = commit.getTree().getId();
            try( ObjectReader reader = git.getRepository().newObjectReader() ) {
                return new CanonicalTreeParser(null, reader, treeId);
            }
        }
    }
}
