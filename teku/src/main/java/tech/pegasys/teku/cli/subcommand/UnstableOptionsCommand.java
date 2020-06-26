/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.cli.subcommand;

import java.io.PrintWriter;
import picocli.CommandLine;
import picocli.CommandLine.Help.ColorScheme;
import picocli.CommandLine.Model.CommandSpec;

/**
 * This class provides a CLI command to enumerate the unstable options available in Teku.
 *
 * <p>Unstable options are distinguished by
 *
 * <ul>
 *   <li>Being marked as 'hidden'
 *   <li>Having their first option name start with <code>--X</code>
 * </ul>
 *
 * There is no stability or compatibility guarantee for options marked as unstable between releases.
 * They can be added and removed without announcement and their meaning and values can similarly
 * change without announcement or warning.
 */
@CommandLine.Command(
    name = "Xhelp",
    aliases = {"-X", "--Xhelp"},
    description = "This command provides help text for all unstable options.",
    hidden = true,
    helpCommand = true)
public class UnstableOptionsCommand implements Runnable, CommandLine.IHelpCommandInitializable2 {

  private CommandLine helpCommandLine;
  private ColorScheme colorScheme;
  private PrintWriter outWriter;

  @Override
  public void run() {
    final CommandSpec commandSpec = helpCommandLine.getParent().getCommandSpec();
    commandSpec.mixins().forEach(this::printUnstableOptions);
  }

  @Override
  public void init(
      final CommandLine helpCommandLine,
      final ColorScheme colorScheme,
      final PrintWriter outWriter,
      final PrintWriter errWriter) {
    this.helpCommandLine = helpCommandLine;
    this.colorScheme = colorScheme;
    this.outWriter = outWriter;
  }

  private void printUnstableOptions(
      final String mixinName, final CommandLine.Model.CommandSpec commandSpec) {
    // Recreate the options but flip hidden to false.
    final CommandLine.Model.CommandSpec cs = CommandLine.Model.CommandSpec.create();
    commandSpec.options().stream()
        .filter(option -> option.hidden() && option.names()[0].startsWith("--X"))
        .forEach(option -> cs.addOption(option.toBuilder().hidden(false).build()));

    if (cs.options().size() > 0) {
      // Print out the help text.
      outWriter.println(mixinName + " unstable options");
      outWriter.println(new CommandLine.Help(cs, colorScheme).optionList());
    }
  }
}
