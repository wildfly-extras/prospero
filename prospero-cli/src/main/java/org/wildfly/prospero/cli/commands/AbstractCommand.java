package org.wildfly.prospero.cli.commands;

import java.util.concurrent.Callable;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.cli.ActionFactory;

public abstract class AbstractCommand implements Callable<Integer> {

    protected final Console console;
    protected final ActionFactory actionFactory;

    public AbstractCommand(Console console, ActionFactory actionFactory) {
        this.console = console;
        this.actionFactory = actionFactory;
    }
}
