package com.redhat.prospero.actions;

import java.util.Collection;
import java.util.List;

import com.redhat.prospero.api.ArtifactChange;
import org.jboss.galleon.layout.FeaturePackUpdatePlan;
import org.jboss.galleon.progresstracking.ProgressCallback;

public interface Console {

    // installation
    void installationComplete();
    //   galleon progress

    ProgressCallback getProgressCallback(String id);
    //   error handling - maybe not, use exceptions and log at the CLI handler


    // update
    void updatesFound(Collection<FeaturePackUpdatePlan> updates, List<ArtifactChange> changes);

    boolean confirmUpdates();

    void updatesComplete();

    void println(String text);
}
