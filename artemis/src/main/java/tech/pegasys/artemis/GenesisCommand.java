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

package tech.pegasys.artemis;

import static tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer.serialize;
import static tech.pegasys.teku.logging.StatusLogger.STDOUT;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.logging.log4j.Level;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.cli.VersionProvider;

@Command(
    name = "genesis",
    description = "Commands for generating genesis state",
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
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
      abbreviateSynopsis = true,
      versionProvider = VersionProvider.class,
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
        STDOUT.log(
            Level.INFO,
            String.format(
                "Generating mock genesis state for %d validators at genesis time %d",
                params.validatorCount, params.genesisTime));
      }

      final long genesisTime = params.genesisTime;
      final List<BLSKeyPair> validatorKeys =
          new MockStartValidatorKeyPairFactory().generateKeyPairs(0, params.validatorCount);
      final BeaconState genesisState =
          StartupUtil.createMockedStartInitialBeaconState(genesisTime, validatorKeys);

      if (outputToFile) {
        STDOUT.log(
            Level.INFO, String.format("Saving genesis state to file: %s", params.outputFile));
      }
      fileStream.write(serialize(genesisState).toArrayUnsafe());
      if (outputToFile) {
        STDOUT.log(Level.INFO, String.format("Genesis state file saved: %s", params.outputFile));
      }
    }
  }

  public static class MockGenesisParams {
    @Option(
        names = {"-o", "--outputFile"},
        paramLabel = "<FILENAME>",
        description = "Path/filename of the output file")
    private String outputFile = null;

    @Option(
        names = {"-v", "--validatorCount"},
        paramLabel = "<VALIDATOR_COUNT>",
        description = "The number of validators to include")
    private int validatorCount = 64;

    @Option(
        names = {"-t", "--genesisTime"},
        paramLabel = "<GENESIS_TIME>",
        description = "The genesis time")
    private long genesisTime = System.currentTimeMillis() / 1000;
  }
}
