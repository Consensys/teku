/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.cli.slashingprotection;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Scanner;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.cli.util.SlashingProtectionCommandUtils;
import tech.pegasys.teku.data.SlashingProtectionRepairer;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.beaconchain.WeakSubjectivityInitializer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;

@CommandLine.Command(
    name = "repair",
    description = "Repair slashing protection data files used by teku.",
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class RepairCommand implements Runnable {
  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();

  @CommandLine.Mixin(name = "Data")
  private ValidatorClientDataOptions dataOptions;

  @CommandLine.Mixin(name = "Network")
  private Eth2NetworkOptions eth2NetworkOptions;

  @CommandLine.Option(
      names = {"--check-only-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Read and report potential problems, but don't update anything.",
      arity = "0..1")
  private boolean checkOnlyEnabled = false;

  @CommandLine.Option(
      names = {"--update-all-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "All validator slashing protection records should be updated.",
      arity = "0..1")
  private boolean updateAllEnabled = false;

  @CommandLine.Option(
      names = {"--slot"},
      paramLabel = "<INTEGER>",
      description =
          "Update slashing protection files to contain the specified slot as a minimum."
              + "Should be a future slot, or after when the validators stopped performing duties.\n"
              + "NOTE: This can be calculated for most networks, and is generally not required.",
      arity = "1")
  private UInt64 suppliedSlot;

  private final WeakSubjectivityInitializer wsInitializer = new WeakSubjectivityInitializer();

  @Override
  public void run() {
    final Path slashProtectionPath =
        SlashingProtectionCommandUtils.getSlashingProtectionPath(dataOptions);
    SlashingProtectionCommandUtils.verifySlashingProtectionPathExists(
        SUB_COMMAND_LOG, slashProtectionPath);
    final Spec spec = eth2NetworkOptions.getNetworkConfiguration().getSpec();

    final Optional<AnchorPoint> initialAnchor =
        wsInitializer.loadInitialAnchorPoint(
            spec, eth2NetworkOptions.getNetworkConfiguration().getInitialState());

    final UInt64 computedSlot = getComputedSlot(initialAnchor, spec);
    final UInt64 computedEpoch =
        spec.atSlot(computedSlot).miscHelpers().computeEpochAtSlot(computedSlot);
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(SUB_COMMAND_LOG, slashProtectionPath, updateAllEnabled);

    if (repairer.hasUpdates()) {
      if (!checkOnlyEnabled) {
        confirmOrExit(computedSlot, computedEpoch);
        repairer.updateRecords(computedSlot, computedEpoch);
      } else {
        SUB_COMMAND_LOG.display("Updates have been skipped as --check-only-enabled was set.");
      }
    }
    SUB_COMMAND_LOG.display("done");
  }

  private UInt64 getComputedSlot(final Optional<AnchorPoint> initialAnchor, final Spec spec) {
    final TimeProvider timeProvider = new SystemTimeProvider();
    if (suppliedSlot != null) {
      displaySlotUpdateMessage(suppliedSlot, spec, "WARNING: using a supplied slot");

      return suppliedSlot;
    } else if (initialAnchor.isPresent()) {
      final UInt64 genesisTime = initialAnchor.get().getState().getGenesis_time();
      final int secondsPerEpoch =
          spec.getGenesisSpec().getSlotsPerEpoch()
              * spec.getGenesisSpec().getConfig().getSecondsPerSlot();
      final UInt64 oneEpochInFuture = timeProvider.getTimeInSeconds().plus(secondsPerEpoch);
      final UInt64 computedSlot = spec.getCurrentSlot(oneEpochInFuture, genesisTime);
      final String network =
          eth2NetworkOptions.getNetwork() == null ? "" : eth2NetworkOptions.getNetwork();
      displaySlotUpdateMessage(computedSlot, spec, "Computed " + network + " slot");
      return computedSlot;
    }
    SUB_COMMAND_LOG.exit(
        1,
        "Could not compute epoch, please supply an epoch to use for updating slashing protection.");
    throw new IllegalStateException("Should not have got past System.exit.");
  }

  private void displaySlotUpdateMessage(
      final UInt64 slot, final Spec spec, final String description) {
    SUB_COMMAND_LOG.display(
        description
            + " "
            + slot
            + ", which will set attestation source/target to epoch "
            + spec.atSlot(slot).miscHelpers().computeEpochAtSlot(slot));
  }

  private void confirmOrExit(final UInt64 slot, final UInt64 epoch) {
    SUB_COMMAND_LOG.display("");
    SUB_COMMAND_LOG.display(
        "REMINDER! If the validator is running, you should not be updating slashing protection records.");
    if (updateAllEnabled) {
      SUB_COMMAND_LOG.display("All valid slashing protection files will also be updated.");
    }
    SUB_COMMAND_LOG.display("block slot -> " + slot);
    SUB_COMMAND_LOG.display("attestation source/target -> " + epoch);
    SUB_COMMAND_LOG.display("Are you sure you wish to continue (yes/no)? ");
    Scanner scanner = new Scanner(System.in, Charset.defaultCharset().name());
    final String confirmation = scanner.next();

    if (!confirmation.equalsIgnoreCase("yes")) {
      SUB_COMMAND_LOG.display("Operation cancelled.");
      System.exit(1);
    }
  }
}
