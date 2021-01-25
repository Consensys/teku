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

package tech.pegasys.teku.cli.subcommand.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.infrastructure.version.VersionProvider;

@Command(
    name = "debug-tools",
    description = "Utilities for debugging issues",
    subcommands = {DebugDbCommand.class},
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    addMethodSubcommands = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DebugToolsCommand implements Runnable {
  @Spec private CommandSpec commandSpec;

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  @Command(
      name = "generate-autocomplete",
      description = "Generate a bash/zsh autocomplete file",
      subcommands = {DebugDbCommand.class},
      showDefaultValues = true,
      abbreviateSynopsis = true,
      mixinStandardHelpOptions = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int autocomplete(
      @Option(
              names = {"--output", "-o"},
              description = "File to output to, default is System.out")
          final Path output) {
    try {
      final String autocompleteScript =
          AutoComplete.bash(VersionProvider.CLIENT_IDENTITY, commandSpec.parent().commandLine());
      if (output != null) {
        Files.writeString(output, autocompleteScript, StandardCharsets.UTF_8);
      } else {
        System.out.println(autocompleteScript);
      }
      return 0;
    } catch (final IOException e) {
      System.err.println("Failed to write autocomplete script: " + e.getMessage());
      return 1;
    }
  }
}
