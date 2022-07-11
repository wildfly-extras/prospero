package org.wildfly.prospero.it;

import java.io.IOException;
import java.util.function.Supplier;

public class ProcessErrorStreamReader implements Supplier<String> {

    private final Process process;

    public ProcessErrorStreamReader(Process process) {
        this.process = process;
    }

    @Override
    public String get() {
        try {
            return "Process error stream:\n" + new String(process.getErrorStream().readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
