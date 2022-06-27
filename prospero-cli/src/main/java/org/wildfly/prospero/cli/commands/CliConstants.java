package org.wildfly.prospero.cli.commands;

/**
 * CLI commands and options names
 */
public final class CliConstants {

    private CliConstants() {
    }

    // Commands:

    public static final String MAIN_COMMAND = "prospero";
    public static final String INSTALL = "install";
    public static final String UPDATE = "update";
    public static final String HISTORY = "history";
    public static final String REVERT = "revert";

    // Options:

    public static final String CHANNEL = "--channel";
    public static final String CHANNEL_REPO = "--channel-repo";
    public static final String DEFINITION = "--definition";
    public static final String DIR = "--dir";
    public static final String DRY_RUN = "--dry-run";
    public static final String FPL = "--fpl";
    public static final String H = "-h"; // shortcut for --help
    public static final String HELP = "--help";
    public static final String LOCAL_REPO = "--local-repo";
    public static final String OFFLINE = "--offline";
    public static final String PROVISION_CONFIG = "--provision-config";
    public static final String REVISION = "--revision";
    public static final String SELF = "--self";
    public static final String Y = "-y";
    public static final String YES = "--yes";
}
