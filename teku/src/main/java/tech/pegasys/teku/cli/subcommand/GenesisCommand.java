/*
 * Copyright 2019 ConsenSys AG.
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

import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.serialize;
import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.datastructures.interop.InteropStartupUtil;
import tech.pegasys.teku.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.datastructures.state.BeaconState;

@Command(
    name = "genesis",
    description = "Commands for generating genesis state",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class GenesisCommand {

  @Command(
      name = "mock",
      description = "Generate a mock genesis state",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public void generate(@Mixin MockGenesisParams params) throws IOException {
    // Output to stdout if no file is specified
    final boolean outputToFile = params.outputFile != null && !params.outputFile.isBlank();
    try (final OutputStream fileStream =
        outputToFile ? new FileOutputStream(params.outputFile) : System.out) {
      if (outputToFile) {
        SUB_COMMAND_LOG.generatingMockGenesis(params.validatorCount, params.genesisTime);
      }

      final long genesisTime = params.genesisTime;
      final List<BLSKeyPair> validatorKeys =
          new MockStartValidatorKeyPairFactory().generateKeyPairs(0, params.validatorCount);
      final BeaconState genesisState =
          InteropStartupUtil.createMockedStartInitialBeaconState(genesisTime, validatorKeys);

      if (outputToFile) {
        SUB_COMMAND_LOG.storingGenesis(params.outputFile, false);
      }
      fileStream.write(serialize(genesisState).toArrayUnsafe());
      if (outputToFile) {
        SUB_COMMAND_LOG.storingGenesis(params.outputFile, true);
      }
    }
  }

  public static class MockGenesisParams {
    @Option(
        names = {"-o", "--output-file"},
        paramLabel = "<FILENAME>",
        description = "Path/filename of the output file\nDefault: stdout")
    private String outputFile = null;

    @Option(
        names = {"-v", "--validator-count"},
        paramLabel = "<VALIDATOR_COUNT>",
        description = "The number of validators to include")
    private int validatorCount = 64;

    @Option(
        names = {"-t", "--genesis-time"},
        paramLabel = "<GENESIS_TIME>",
        description = "The genesis time")
    private long genesisTime = System.currentTimeMillis() / 1000;
  }
}
