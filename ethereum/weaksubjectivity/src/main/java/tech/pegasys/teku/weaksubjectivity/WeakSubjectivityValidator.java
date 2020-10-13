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

package tech.pegasys.teku.weaksubjectivity;

import static tech.pegasys.teku.core.ForkChoiceUtil.get_ancestor;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.Throttler;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;
import tech.pegasys.teku.weaksubjectivity.policies.WeakSubjectivityViolationPolicy;

public class WeakSubjectivityValidator {
  private static final Logger LOG = LogManager.getLogger();
  // About a week with mainnet config
  static final long MAX_SUPPRESSED_EPOCHS = 1575;

  private final WeakSubjectivityCalculator calculator;
  private final WeakSubjectivityViolationPolicy violationPolicy;

  private final WeakSubjectivityConfig config;
  private volatile Optional<UInt64> suppressWSPeriodErrorsUntilEpoch = Optional.empty();
  private final Throttler<StatusLogger> wsChecksSuppressedLogger =
      new Throttler<>(STATUS_LOG, UInt64.valueOf(10));
  private final Throttler<StatusLogger> deferValidationLogger =
      new Throttler<>(STATUS_LOG, UInt64.valueOf(10));

  WeakSubjectivityValidator(
      final WeakSubjectivityConfig config,
      WeakSubjectivityCalculator calculator,
      WeakSubjectivityViolationPolicy violationPolicy) {
    this.calculator = calculator;
    this.violationPolicy = violationPolicy;
    this.config = config;
  }

  public static WeakSubjectivityValidator strict(final WeakSubjectivityConfig config) {
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    return new WeakSubjectivityValidator(
        config, calculator, WeakSubjectivityViolationPolicy.strict());
  }

  public static WeakSubjectivityValidator moderate(final WeakSubjectivityConfig config) {
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    return new WeakSubjectivityValidator(
        config, calculator, WeakSubjectivityViolationPolicy.moderate(config));
  }

  public static WeakSubjectivityValidator lenient() {
    return lenient(WeakSubjectivityConfig.defaultConfig());
  }

  public static WeakSubjectivityValidator lenient(final WeakSubjectivityConfig config) {
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    return new WeakSubjectivityValidator(
        config, calculator, WeakSubjectivityViolationPolicy.lenient());
  }

  public Optional<Checkpoint> getWSCheckpoint() {
    return config.getWeakSubjectivityCheckpoint();
  }

  /** Check whether the chain matches any configured weak subjectivity checkpoint or state */
  public SafeFuture<Void> validateChainIsConsistentWithWSCheckpoint(
      CombinedChainDataClient chainData) {
    if (config.getWeakSubjectivityCheckpoint().isEmpty()) {
      // Nothing to validate against
      return SafeFuture.COMPLETE;
    }
    final Checkpoint wsCheckpoint = config.getWeakSubjectivityCheckpoint().get();
    if (!chainData.isFinalizedEpoch(wsCheckpoint.getEpoch())) {
      // Checkpoint is in the future - nothing to validate yet
      return SafeFuture.COMPLETE;
    }

    return chainData
        .getBlockInEffectAtSlot(wsCheckpoint.getEpochStartSlot())
        .thenAccept(
            maybeBlock -> {
              // We must have a block at this slot because we know this epoch is finalized
              SignedBeaconBlock blockAtCheckpointSlot = maybeBlock.orElseThrow();
              if (!blockAtCheckpointSlot.getRoot().equals(wsCheckpoint.getRoot())) {
                handleInconsistentWsCheckpoint(blockAtCheckpointSlot);
              }
            });
  }

  /**
   * Validates that the latest finalized checkpoint is within the weak subjectivity period, given
   * the current slot based on clock time. If validation fails, configured policies are run to
   * handle this failure.
   *
   * @param latestFinalizedCheckpoint The latest finalized checkpoint
   * @param currentSlot The current slot based on clock time
   */
  public void validateLatestFinalizedCheckpoint(
      final CheckpointState latestFinalizedCheckpoint, final UInt64 currentSlot) {
    if (isPriorToWSCheckpoint(latestFinalizedCheckpoint)) {
      // Defer validation until we reach the weakSubjectivity checkpoint
      final UInt64 finalizedEpoch = latestFinalizedCheckpoint.getEpoch();
      final UInt64 wsEpoch = config.getWeakSubjectivityCheckpoint().orElseThrow().getEpoch();
      deferValidationLogger.invoke(
          currentSlot,
          l ->
              l.warnWeakSubjectivityFinalizedCheckpointValidationDeferred(finalizedEpoch, wsEpoch));
      return;
    }

    // Validate against ws checkpoint
    if (isAtWSCheckpoint(latestFinalizedCheckpoint)
        && !isWSCheckpointRoot(latestFinalizedCheckpoint.getRoot())) {
      // Finalized root is inconsistent with ws checkpoint
      handleInconsistentWsCheckpoint(latestFinalizedCheckpoint.getBlock());
    }

    // Determine whether we should suppress ws period errors
    UInt64 currentEpoch = compute_epoch_at_slot(currentSlot);
    final Optional<UInt64> suppressionEpoch = getSuppressWSPeriodChecksUntilEpoch(currentSlot);
    final boolean shouldSuppressErrors =
        suppressionEpoch.map(e -> e.isGreaterThan(currentEpoch)).orElse(false);

    // Validate against ws period
    final boolean withinWSPeriod = isWithinWSPeriod(latestFinalizedCheckpoint, currentSlot);
    if (!withinWSPeriod && !shouldSuppressErrors) {
      handleFinalizedCheckpointOutsideWSPeriod(latestFinalizedCheckpoint, currentSlot);
    } else if (!withinWSPeriod) {
      wsChecksSuppressedLogger.invoke(
          currentSlot, l -> l.warnWeakSubjectivityChecksSuppressed(suppressionEpoch.orElseThrow()));
    }
  }

  public boolean isBlockValid(
      final SignedBeaconBlock block, ForkChoiceStrategy forkChoiceStrategy) {
    if (config.getWeakSubjectivityCheckpoint().isEmpty()) {
      return true;
    }
    final Checkpoint wsCheckpoint = config.getWeakSubjectivityCheckpoint().get();

    UInt64 blockEpoch = compute_epoch_at_slot(block.getSlot());
    boolean blockAtEpochBoundary = compute_start_slot_at_epoch(blockEpoch).equals(block.getSlot());
    if (isWSCheckpointEpoch(blockEpoch) && blockAtEpochBoundary) {
      // Block is at ws checkpoint slot - so it must match the ws checkpoint block
      return isWSCheckpointBlock(block);
    } else if (isWSCheckpointBlock(block)) {
      // The block is the checkpoint
      return true;
    } else if (blockEpoch.isGreaterThanOrEqualTo(wsCheckpoint.getEpoch())) {
      // If the block is at or past the checkpoint, the wsCheckpoint must be an ancestor
      final Optional<Bytes32> ancestor =
          get_ancestor(
              forkChoiceStrategy, block.getParent_root(), wsCheckpoint.getEpochStartSlot());
      // If ancestor is not present, the chain must have moved passed the wsCheckpoint
      return ancestor.map(a -> a.equals(wsCheckpoint.getRoot())).orElse(true);
    }

    // If block is prior to the checkpoint, we can't yet validate
    // TODO(#2779) If we have the ws state, we can look up the block in the state's history
    return true;
  }

  /**
   * A catch-all handler for managing problems encountered while executing other validations
   *
   * @param message An error message
   * @param error The error encountered
   */
  public void handleValidationFailure(final String message, Throwable error) {
    violationPolicy.onFailedToPerformValidation(message, error);
  }

  private void handleFinalizedCheckpointOutsideWSPeriod(
      final CheckpointState latestFinalizedCheckpoint, final UInt64 currentSlot) {
    final int activeValidators =
        calculator.getActiveValidators(latestFinalizedCheckpoint.getState());
    final UInt64 wsPeriod = calculator.computeWeakSubjectivityPeriod(activeValidators);
    violationPolicy.onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
        latestFinalizedCheckpoint, activeValidators, currentSlot, wsPeriod);
  }

  private void handleInconsistentWsCheckpoint(final SignedBeaconBlock inconsistentBlock) {
    final Checkpoint wsCheckpoint = config.getWeakSubjectivityCheckpoint().orElseThrow();
    violationPolicy.onChainInconsistentWithWeakSubjectivityCheckpoint(
        wsCheckpoint, inconsistentBlock);
  }

  @VisibleForTesting
  Optional<UInt64> getSuppressWSPeriodChecksUntilEpoch(final UInt64 currentSlot) {
    if (suppressWSPeriodErrorsUntilEpoch.isEmpty()
        && config.getSuppressWSPeriodChecksUntilEpoch().isPresent()) {
      // Initialize the suppression logic
      final UInt64 configuredSuppressionEpoch = config.getSuppressWSPeriodChecksUntilEpoch().get();
      final UInt64 startupEpoch = compute_epoch_at_slot(currentSlot);
      final UInt64 maxSuppressedEpoch = startupEpoch.plus(MAX_SUPPRESSED_EPOCHS);
      final UInt64 suppressionEpoch = configuredSuppressionEpoch.min(maxSuppressedEpoch);
      if (suppressionEpoch.isLessThan(configuredSuppressionEpoch)) {
        LOG.info(
            "Configured weak subjectivity error suppression epoch ({}) is too large, suppression epoch set to {} ({} epochs ahead of current epoch {}).",
            configuredSuppressionEpoch,
            suppressionEpoch,
            MAX_SUPPRESSED_EPOCHS,
            startupEpoch);
      }
      LOG.warn(
          "Configured to suppress weak subjectivity period checks until epoch {}",
          suppressionEpoch);

      suppressWSPeriodErrorsUntilEpoch = Optional.of(suppressionEpoch);
    }

    return suppressWSPeriodErrorsUntilEpoch;
  }

  private boolean isWithinWSPeriod(CheckpointState checkpointState, UInt64 currentSlot) {
    return calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot);
  }

  private boolean isWSCheckpointEpoch(final UInt64 epoch) {
    return config
        .getWeakSubjectivityCheckpoint()
        .map(c -> c.getEpoch().equals(epoch))
        .orElse(false);
  }

  private boolean isWSCheckpointBlock(final SignedBeaconBlock block) {
    return isWSCheckpointRoot(block.getRoot());
  }

  private boolean isWSCheckpointRoot(final Bytes32 blockRoot) {
    return config
        .getWeakSubjectivityCheckpoint()
        .map(c -> c.getRoot().equals(blockRoot))
        .orElse(false);
  }

  private boolean isPriorToWSCheckpoint(final CheckpointState checkpoint) {
    return config
        .getWeakSubjectivityCheckpoint()
        .map(c -> checkpoint.getEpoch().isLessThan(c.getEpoch()))
        .orElse(false);
  }

  private boolean isAtWSCheckpoint(final CheckpointState checkpoint) {
    return config
        .getWeakSubjectivityCheckpoint()
        .map(c -> checkpoint.getEpoch().equals(c.getEpoch()))
        .orElse(false);
  }
}
