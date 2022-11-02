/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.cli;

import picocli.CommandLine;

/**
 * Modified implementation of usage renderer.
 *
 * This mainly modifies the order of options in the usage message from the default behavior:
 * <li>print option groups first,</li>
 * <li>sort by order argument.</li>
 */
public class CustomHelp extends CommandLine.Help {

    public static class CustomHelpFactory implements CommandLine.IHelpFactory {
        public CommandLine.Help create(CommandLine.Model.CommandSpec commandSpec, CommandLine.Help.ColorScheme colorScheme) {
            return new CustomHelp(commandSpec, colorScheme);
        }
    }

    private static boolean empty(Object[] array) {
        return array == null || array.length == 0;
    }

    private final CommandLine.Model.CommandSpec commandSpec;

    public CustomHelp(CommandLine.Model.CommandSpec commandSpec, ColorScheme colorScheme) {
        super(commandSpec, colorScheme);
        this.commandSpec = commandSpec;
    }

    /**
     * Override this to set custom options compatarator and clusterBooleanOptions parameter to false.
     */
    @Override
    public String synopsis(int synopsisHeadingLength) {
        if (!empty(commandSpec.usageMessage().customSynopsis())) { return customSynopsis(); }
        return commandSpec.usageMessage().abbreviateSynopsis() ? abbreviatedSynopsis()
                : detailedSynopsis(synopsisHeadingLength, createDefaultOptionSort(), false);
    }

    /**
     * Override this to switch positions of groupsText and optionText.
     */
    @Override
    protected String makeSynopsisFromParts(int synopsisHeadingLength, Ansi.Text optionText, Ansi.Text groupsText, Ansi.Text endOfOptionsText, Ansi.Text positionalParamText, Ansi.Text commandText) {
        boolean positionalsOnly = true;
        for (CommandLine.Model.ArgGroupSpec group : commandSpec().argGroups()) {
            if (group.validate()) { // non-validating groups are not shown in the synopsis
                positionalsOnly &= group.allOptionsNested().isEmpty();
            }
        }
        Ansi.Text text;
        if (positionalsOnly) { // show end-of-options delimiter before the (all-positional params) groups
            text = optionText.concat(endOfOptionsText).concat(groupsText).concat(positionalParamText).concat(commandText);
        } else {
            text = groupsText.concat(optionText).concat(endOfOptionsText).concat(positionalParamText).concat(commandText);
        }
        return insertSynopsisCommandName(synopsisHeadingLength, text);
    }
}
