package org.wildfly.prospero.actions;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;

import org.jboss.galleon.layout.FeaturePackUpdatePlan;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.wildfly.prospero.api.ArtifactChange;

public interface Console {

    // installation
    void installationComplete();
    //   galleon progress

    ProgressCallback<?> getProgressCallback(String id);
    //   error handling - maybe not, use exceptions and log at the CLI handler


    // update
    void updatesFound(Collection<FeaturePackUpdatePlan> updates, List<ArtifactChange> changes);

    boolean confirmUpdates();

    void updatesComplete();

    default void println(String text) {
        getStdOut().println(text);
    }

    default void error(String message, String... args) {
        getErrOut().println(String.format(message, args));
    }

    default PrintStream getStdOut() {
        return System.out;
    }

    default PrintStream getErrOut() {
        return System.err;
    }

    default InputStream getInput() {
        return System.in;
    }
}
