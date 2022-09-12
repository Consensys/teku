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

package tech.pegasys.teku.infrastructure.logging;

import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatBlock;

import com.google.common.base.Strings;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.Color;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorLogger {
  private static final int VALIDATOR_KEY_LIMIT = 20;
  public static final ValidatorLogger VALIDATOR_LOGGER =
      new ValidatorLogger(LoggingConfigurator.VALIDATOR_LOGGER_NAME);
  public static final int LONGEST_TYPE_LENGTH = "sync_contribution".length();
  private static final String PREFIX = "Validator   *** ";

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger log;

  private ValidatorLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void connectedToBeaconNodeEventStream() {
    log.info(
        ColorConsolePrinter.print(
            String.format("%sSuccessfully connected to beacon node event stream", PREFIX),
            Color.GREEN));
  }

  public void beaconNodeEventStreamConnectionError(final Throwable t) {
    log.error(
        ColorConsolePrinter.print(
            String.format("%sError while connecting to beacon node event stream", PREFIX),
            Color.RED),
        t);
  }

  public void switchingToFailoverBeaconNodeForEventStreaming(final URI eventStreamEndpoint) {
    log.info(
        ColorConsolePrinter.print(
            String.format(
                "%sSwitching to failover beacon node for event streaming: %s",
                PREFIX, eventStreamEndpoint),
            Color.GREEN));
  }

  public void primaryBeaconNodeIsBackOnlineForEventStreaming() {
    log.info(
        ColorConsolePrinter.print(
            String.format(
                "%sPrimary beacon node is back and ready for event streaming. Will attempt connecting.",
                PREFIX),
            Color.GREEN));
  }

  public void dutyCompleted(
      final String producedType,
      final UInt64 slot,
      final int successCount,
      final Set<Bytes32> blockRoots,
      final Optional<String> context) {
    final String paddedType = Strings.padEnd(producedType, LONGEST_TYPE_LENGTH, ' ');
    logDuty(paddedType, slot, successCount, blockRoots, context);
  }

  public void dutySkippedWhileSyncing(
      final String producedType, final UInt64 slot, final int skippedCount) {
    log.warn(
        ColorConsolePrinter.print(
            String.format(
                "%sSkipped producing %s while node is syncing  Count: %s, Slot: %s",
                PREFIX, producedType, skippedCount, slot),
            Color.YELLOW));
  }

  public void dutyFailed(
      final String producedType,
      final UInt64 slot,
      final Set<String> maybeKey,
      final Throwable error) {
    final String errorString =
        String.format(
            "%sFailed to produce %s  Slot: %s Validator: %s",
            PREFIX, producedType, slot, formatValidators(maybeKey));
    log.error(ColorConsolePrinter.print(errorString, Color.RED), error);
  }

  public void signerNoLongerActive(
      final String producedType, final UInt64 slot, final Set<String> maybeKey) {
    final String errorString =
        String.format(
            "%sValidator removed, skipping previously scheduled %s production. Slot: %s Validator: %s",
            PREFIX, producedType, slot, formatValidators(maybeKey));
    log.info(ColorConsolePrinter.print(errorString, Color.YELLOW));
  }

  private String formatValidators(final Set<String> keys) {
    if (keys.isEmpty()) {
      return "";
    }
    final String suffix = keys.size() > VALIDATOR_KEY_LIMIT ? "… (" + keys.size() + " total)" : "";
    return keys.stream().limit(VALIDATOR_KEY_LIMIT).collect(Collectors.joining(", ", "", suffix));
  }

  private void logDuty(
      final String type,
      final UInt64 slot,
      final int count,
      final Set<Bytes32> roots,
      final Optional<String> context) {
    log.info(
        ColorConsolePrinter.print(
            String.format(
                "%sPublished %s  Count: %s, Slot: %s, Root: %s%s",
                PREFIX,
                type,
                count,
                slot,
                formatBlockRoots(roots),
                context.map(s -> ", " + s).orElse("")),
            Color.BLUE));
  }

  private String formatBlockRoots(final Set<Bytes32> blockRoots) {
    return blockRoots.stream().map(LogFormatter::formatHashRoot).collect(Collectors.joining(", "));
  }

  public void aggregationSkipped(final UInt64 slot, final int committeeIndex) {
    log.warn(
        ColorConsolePrinter.print(
            PREFIX
                + "Skipped aggregation for committee "
                + committeeIndex
                + " at slot "
                + slot
                + " because there was nothing to aggregate",
            Color.YELLOW));
  }

  public void syncCommitteeAggregationSkipped(final UInt64 slot) {
    log.warn(
        ColorConsolePrinter.print(
            PREFIX
                + "Skipped aggregation at slot "
                + slot
                + " because there was nothing to aggregate",
            Color.YELLOW));
  }

  public void producedInvalidAttestation(final UInt64 slot, final String reason) {
    log.error(
        ColorConsolePrinter.print(
            PREFIX
                + "Produced invalid attestation for slot "
                + slot
                + ". Invalid reason: "
                + reason,
            Color.RED));
  }

  public void producedInvalidAggregate(final UInt64 slot, final String reason) {
    log.error(
        ColorConsolePrinter.print(
            PREFIX + "Produced invalid aggregate for slot " + slot + ": " + reason, Color.RED));
  }

  public void beaconProposerPreparationFailed(final Throwable error) {
    final String errorString = String.format("%sFailed to send proposers to Beacon Node", PREFIX);
    log.error(ColorConsolePrinter.print(errorString, Color.RED), error);
  }

  public void registeringValidatorsFailed(final Throwable error) {
    final String errorString =
        String.format(
            "%sFailed to send validator registrations to the builder network via the Beacon Node",
            PREFIX);
    log.error(ColorConsolePrinter.print(errorString, Color.RED), error);
  }

  public void executionPayloadPreparedUsingBeaconDefaultFeeRecipient(final UInt64 slot) {
    log.warn(
        ColorConsolePrinter.print(
            "Beacon Node has been requested to produce a block for slot "
                + slot
                + " but proposer hasn't been prepared by any Validator Client. "
                + "Using Beacon Node's default fee recipient.",
            Color.YELLOW));
  }

  public void executionPayloadPreparedUsingBurnAddressForFeeRecipient(final UInt64 slot) {
    log.error(
        ColorConsolePrinter.print(
            "Producing block at slot "
                + slot
                + ", no proposer was prepared, and no default fee recipient defined, inclusion fees will be lost",
            Color.RED));
  }

  public void proposedBlockImportFailed(
      final String failureReason,
      final UInt64 slot,
      final Bytes32 root,
      final Optional<Throwable> error) {
    log.error(
        ColorConsolePrinter.print(
            "Failed to import proposed block due to "
                + failureReason
                + ": "
                + formatBlock(slot, root),
            Color.RED),
        error.orElse(null));
  }

  public void loadedSlashingProtection(final Set<String> maybeKey) {
    final String infoString =
        String.format(
            "%sSlashing protection loaded for validators: %s", PREFIX, formatValidators(maybeKey));
    log.info(ColorConsolePrinter.print(infoString, Color.GREEN));
  }

  public void notLoadedSlashingProtection(final Set<String> maybeKey) {
    final String infoString =
        String.format(
            "%sSlashing protection not loaded for validators: %s",
            PREFIX, formatValidators(maybeKey));
    log.warn(ColorConsolePrinter.print(infoString, Color.YELLOW));
  }

  public void outdatedSlashingProtection(final Set<String> maybeKey, final UInt64 deltaEpochs) {
    final String infoString =
        String.format(
            "%sSlashing protection last updated more than %s epochs ago for validators: %s",
            PREFIX, deltaEpochs, formatValidators(maybeKey));
    log.warn(ColorConsolePrinter.print(infoString, Color.YELLOW));
  }
}
