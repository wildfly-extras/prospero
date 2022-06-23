package org.wildfly.prospero.cli.commands;

import java.util.concurrent.Callable;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

@CommandLine.Command(name = CliConstants.MAIN_COMMAND)
public class MainCommand implements Callable<Integer> {

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    private Console console;

    public MainCommand(Console console) {
        this.console = console;
    }

    @Override
    public Integer call() {
        console.println(CommandLine.Help.Ansi.AUTO.string("@|bold Welcome to Prospero CLI!|@"));
        console.println("");
        console.println(
                "This tool allows you to provision instannces of Wildfly or JBoss EAP application containers.");
        spec.commandLine().usage(System.out);
        console.println("");
        console.println("Use `prospero <command> --help` to show usage information about given command.");
        return ReturnCodes.SUCCESS;
    }

}
