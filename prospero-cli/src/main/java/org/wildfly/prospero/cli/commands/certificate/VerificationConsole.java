package org.wildfly.prospero.cli.commands.certificate;

import static org.jboss.galleon.Constants.TRACK_CONFIGS;
import static org.jboss.galleon.Constants.TRACK_LAYOUT_BUILD;
import static org.jboss.galleon.Constants.TRACK_PACKAGES;
import static org.wildfly.prospero.galleon.GalleonEnvironment.TRACK_JBEXAMPLES;
import static org.wildfly.prospero.galleon.GalleonEnvironment.TRACK_JBMODULES;
import static org.wildfly.prospero.galleon.GalleonEnvironment.TRACK_JB_ARTIFACTS_RESOLVE;
import static org.wildfly.prospero.galleon.GalleonEnvironment.TRACK_RESOLVING_VERSIONS;

import java.util.Timer;
import java.util.TimerTask;

import org.wildfly.prospero.actions.CertificateAction;
import org.wildfly.prospero.api.ProvisioningProgressEvent;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;

public class VerificationConsole implements CertificateAction.VerificationListener, AutoCloseable {

    private final CliConsole console;
    private TimerTask task;

    public VerificationConsole(CliConsole console) {
        this.console = console;
    }

    @Override
    public void close() {
        timer.cancel();
    }

    private final LinePrinter linePrinter = new LinePrinter();

    private class LinePrinter {
        private int lastLength = 0;

        synchronized void print(String text) {
            eraseLastLine();
            lastLength = text.length();
            console.printf(text);
        }

        private void eraseLastLine() {
            console.printf("\r" + " ".repeat(Math.max(0, lastLength)) + "\r");
        }
    }

    private final Timer timer = new Timer();

    @Override
    public void progressUpdate(ProvisioningProgressEvent update) {
        if (update.getEventType() == ProvisioningProgressEvent.EventType.STARTING) {
            final String currentState = toText(update.getStage());
            task = new ProgressPrinterTask(currentState);
            timer.scheduleAtFixedRate(task, 0, 100);

        } else if (update.getEventType() == ProvisioningProgressEvent.EventType.COMPLETED) {
            task.cancel();
            final String line = toText(update.getStage()) + " DONE";
            linePrinter.print(line);
        }
    }

    @Override
    public void provisionReferenceServerStarted() {
        console.println("Generating reference server");
    }

    @Override
    public void provisionReferenceServerFinished() {
        task.cancel();
        console.println("");
        linePrinter.lastLength = 0;
    }

    @Override
    public void validatingComponentsStarted() {
        task = new ProgressPrinterTask("Validating component signatures");
        timer.schedule(task, 0, 200);
    }

    @Override
    public void validatingComponentsFinished() {
        task.cancel();
        linePrinter.print("Validating component signatures DONE");
        console.println("");
        linePrinter.lastLength = 0;
    }

    @Override
    public void checkingModifiedFilesStarted() {
        task = new ProgressPrinterTask("Checking for locally modified files");
        timer.schedule(task, 0, 200);
    }

    @Override
    public void checkingModifiedFilesFinished() {
        task.cancel();
        linePrinter.print("Checking for locally modified files DONE");
        console.println("");
        linePrinter.lastLength = 0;
    }

    private String toText(String stage) {
        switch (stage) {
            case TRACK_LAYOUT_BUILD:
                return CliMessages.MESSAGES.resolvingFeaturePack();
            case TRACK_PACKAGES:
                return CliMessages.MESSAGES.installingPackages();
            case TRACK_CONFIGS:
                return CliMessages.MESSAGES.generatingConfiguration();
            case TRACK_JBMODULES:
                return CliMessages.MESSAGES.installingJBossModules();
            case TRACK_JBEXAMPLES:
                return CliMessages.MESSAGES.installingJBossExamples();
            case TRACK_JB_ARTIFACTS_RESOLVE:
                return CliMessages.MESSAGES.downloadingArtifacts();
            case TRACK_RESOLVING_VERSIONS:
                return CliMessages.MESSAGES.resolvingVersions();
            default:
                return stage;
        }
    }

    private class ProgressPrinterTask extends TimerTask {
        private final String currentState;
        int counter;

        public ProgressPrinterTask(String currentState) {
            this.currentState = currentState;
            counter = 0;
        }

        @Override
        public void run() {
            counter++;
            if (counter > 10) {
                counter = 1;
            }
            final String progress = ".".repeat(Math.max(0, counter));
            final String line2 = currentState + " " + progress;
            linePrinter.print(line2);
        }
    }
}
