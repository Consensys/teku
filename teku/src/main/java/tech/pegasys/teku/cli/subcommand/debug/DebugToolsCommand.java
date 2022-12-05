/*
 * Copyright ConsenSys Software Inc., 2022
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

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.restapi.RestApi;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.service.serviceutils.layout.SeparateServiceDataDirLayout;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.NoOpKeyManager;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApi;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApiConfig;

@Command(
    name = "debug-tools",
    description = "Utilities for debugging issues",
    subcommands = {DebugDbCommand.class, PrettyPrintCommand.class},
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

  @Command(
      name = "generate-swagger-docs",
      description = "Generate swagger-docs for rest APIs.",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int generateSwaggerDocs(
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "Directory to write swagger docs to.")
          final Path outputPath)
      throws Exception {
    if (!outputPath.toFile().mkdirs() && !outputPath.toFile().isDirectory()) {
      throw new InvalidConfigurationException(
          String.format(
              "Destination path %s could not be created or is not a directory",
              outputPath.toAbsolutePath()));
    }
    ValidatorRestApiConfig config =
        ValidatorRestApiConfig.builder().restApiDocsEnabled(true).build();

    final Path tempDir = Files.createTempDirectory("teku_debug_tools");
    if (!tempDir.toFile().mkdirs() && !tempDir.toFile().isDirectory()) {
      System.err.println("Could not create temp directory");
      return 1;
    }
    tempDir.toFile().deleteOnExit();

    DataDirLayout dataDirLayout =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    final KeyManager keyManager = new NoOpKeyManager();
    RestApi api =
        ValidatorRestApi.create(
            config,
            Optional.empty(),
            keyManager,
            dataDirLayout,
            Optional.empty(),
            Optional.empty());

    if (api.getRestApiDocs().isPresent()) {
      final String docs = api.getRestApiDocs().get();
      final Path validatorApiPath = outputPath.resolve("validator-api.json");
      System.out.println("Writing validator-api to " + validatorApiPath.toAbsolutePath());
      try (FileWriter fileWriter =
          new FileWriter(validatorApiPath.toFile(), StandardCharsets.UTF_8)) {
        fileWriter.write(docs);
      } catch (IOException e) {
        System.err.println("Failed to write validator-api.json: " + e.getMessage());
        return 1;
      }
    } else {
      System.err.println("Failed to create rest api document for the validator api.");
      return 1;
    }

    return 0;
  }

  @Command(
      name = "get-validator-assignment",
      description = "Gets the committee assignment for a validator at a specific epoch.",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int printCommittees(
      @Option(
              required = true,
              names = {"--state", "-s"},
              description = "Starting state to use. Must be in or before the specified epoch.")
          final Path statePath,
      @Option(
              required = true,
              names = {"--validator", "-v"},
              description = "Validator index to get assignment for")
          final int validatorIndex,
      @Option(
              required = true,
              names = {"--epoch", "-e"},
              description = "Epoch to get assignment for")
          final long epoch,
      @Option(
              defaultValue = "mainnet",
              names = {"--network", "-n"},
              description = "Represents which network to use.")
          final String network)
      throws Exception {
    final tech.pegasys.teku.spec.Spec spec = SpecFactory.create(network);
    BeaconState state = spec.deserializeBeaconState(Bytes.wrap(Files.readAllBytes(statePath)));

    if (spec.getCurrentEpoch(state).isLessThan(epoch)) {
      state = spec.processSlots(state, spec.computeStartSlotAtEpoch(UInt64.valueOf(epoch)));
    }
    final CommitteeAssignment assignment =
        spec.getCommitteeAssignment(state, UInt64.valueOf(epoch), validatorIndex).orElseThrow();
    System.out.printf(
        "Validator %s assigned to attest at slot %s in committee index %s, position %s%n",
        validatorIndex,
        assignment.getSlot(),
        assignment.getCommitteeIndex(),
        assignment.getCommittee().indexOf(validatorIndex));
    return 0;
  }
}
