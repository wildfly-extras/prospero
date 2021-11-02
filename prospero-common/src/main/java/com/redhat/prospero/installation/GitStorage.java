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

package com.redhat.prospero.installation;

import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.SavedState;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GitStorage {

    public static final String GIT_HISTORY_USER = "EAP Installer";
    public static final PersonIdent GIT_HISTORY_COMMITTER = new PersonIdent(GIT_HISTORY_USER, "");
    private Path base;

    public GitStorage(Path base) {
        this.base = base;
    }

    public List<SavedState> getRevisions() {
        try {
            Git git = getRepository();

            final Iterable<RevCommit> call = git.log().call();
            List<SavedState> history = new ArrayList<>();
            for (RevCommit revCommit : call) {
                history.add(new SavedState(revCommit.getName().substring(0,8),
                        Instant.ofEpochSecond(revCommit.getCommitTime()),
                        SavedState.Type.valueOf(revCommit.getShortMessage().toUpperCase(Locale.ROOT))));
            }

            return history;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoFilepatternException e) {
            e.printStackTrace();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return null; // TODO: throw exception
    }

    public void record() {
        try {
            Git git = getRepository();
            git.add().addFilepattern(InstallationMetadata.MANIFEST_FILE_NAME).call();

            if (isRepositoryEmpty(git)) {
                git.commit().setCommitter(GIT_HISTORY_COMMITTER).setMessage(SavedState.Type.INSTALL.name()).call();
            } else {
                git.commit().setCommitter(GIT_HISTORY_COMMITTER).setMessage(SavedState.Type.UPDATE.name()).call();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoFilepatternException e) {
            e.printStackTrace();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
    }

    public void revert(SavedState savedState) {
        try {
            final Git git = getRepository();
            git.checkout()
                    .setStartPoint(savedState.getName())
                    .addPath(InstallationMetadata.MANIFEST_FILE_NAME)
                    .call();
            git.add().addFilepattern(InstallationMetadata.MANIFEST_FILE_NAME).call();
            git.commit().setCommitter(GIT_HISTORY_COMMITTER).setMessage(SavedState.Type.ROLLBACK.name()).call();
        } catch (GitAPIException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isRepositoryEmpty(Git git) throws IOException {
        return git.getRepository().resolve(Constants.HEAD) == null;
    }

    private Git getRepository() throws GitAPIException, IOException {
        Git git;
        if (!base.resolve(".git").toFile().exists()) {
            git = Git.init().setDirectory(base.toFile()).call();
        } else {
            git = Git.open(base.toFile());
        }
        return git;
    }
}
