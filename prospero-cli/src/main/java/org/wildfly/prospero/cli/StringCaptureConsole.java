package org.wildfly.prospero.cli;

import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.ProvisioningProgressEvent;

import java.util.ArrayList;
import java.util.List;

public class StringCaptureConsole implements Console {
    private List<String> lines = new ArrayList<>();

    @Override
    public void progressUpdate(ProvisioningProgressEvent update) {
        //no operations
    }

    @Override
    public void println(String text) {
        lines.add(text);
    }

    public List<String> getLines() {
        return lines;
    }

    public List<String> getLines(int indent) {
        final List<String> indentedLines = new ArrayList<>();
        for (String line : lines) {
            indentedLines.add(" ".repeat(indent) + line);
        }
        return indentedLines;
    }
}
