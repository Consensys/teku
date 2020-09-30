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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;
import tech.pegasys.teku.weaksubjectivity.policies.LoggingWeakSubjectivityViolationPolicy;
import tech.pegasys.teku.weaksubjectivity.policies.StrictWeakSubjectivityViolationPolicy;
import tech.pegasys.teku.weaksubjectivity.policies.WeakSubjectivityViolationPolicy;

public class WeakSubjectivityValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final WeakSubjectivityCalculator calculator;
  private final List<WeakSubjectivityViolationPolicy> violationPolicies;

  private final WeakSubjectivityConfig config;

  WeakSubjectivityValidator(
      final WeakSubjectivityConfig config,
      WeakSubjectivityCalculator calculator,
      List<WeakSubjectivityViolationPolicy> violationPolicies) {
    this.calculator = calculator;
    this.violationPolicies = violationPolicies;
    this.config = config;
  }

  public static WeakSubjectivityValidator strict(final WeakSubjectivityConfig config) {
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    final List<WeakSubjectivityViolationPolicy> policies =
        List.of(
            new LoggingWeakSubjectivityViolationPolicy(Level.FATAL),
            new StrictWeakSubjectivityViolationPolicy());
    return new WeakSubjectivityValidator(config, calculator, policies);
  }

  public static WeakSubjectivityValidator lenient() {
    return lenient(WeakSubjectivityConfig.defaultConfig());
  }

  public static WeakSubjectivityValidator lenient(final WeakSubjectivityConfig config) {
    final WeakSubjectivityCalculator calculator = WeakSubjectivityCalculator.create(config);
    final List<WeakSubjectivityViolationPolicy> policies =
        List.of(new LoggingWeakSubjectivityViolationPolicy(Level.TRACE));
    return new WeakSubjectivityValidator(config, calculator, policies);
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
                for (WeakSubjectivityViolationPolicy policy : violationPolicies) {
                  policy.onChainInconsistentWithWeakSubjectivityCheckpoint(
                      wsCheckpoint, blockAtCheckpointSlot);
                }
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
      LOG.debug(
          "Latest finalized checkpoint at epoch {} is prior to weak subjectivity checkpoint at epoch {}. Defer validation.",
          latestFinalizedCheckpoint.getEpoch(),
          config.getWeakSubjectivityCheckpoint().orElseThrow().getEpoch());
      return;
    }

    // Determine validity
    boolean isValid = true;
    if (isAtWSCheckpoint(latestFinalizedCheckpoint)) {
      // Roots must match
      isValid = isWSCheckpointRoot(latestFinalizedCheckpoint.getRoot());
    }
    isValid = isValid && isWithinWSPeriod(latestFinalizedCheckpoint, currentSlot);

    // Handle invalid checkpoint
    if (!isValid) {
      final int activeValidators =
          calculator.getActiveValidators(latestFinalizedCheckpoint.getState());
      for (WeakSubjectivityViolationPolicy policy : violationPolicies) {
        policy.onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            latestFinalizedCheckpoint, activeValidators, currentSlot);
      }
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
   */
  public void handleValidationFailure(final String message) {
    for (WeakSubjectivityViolationPolicy policy : violationPolicies) {
      policy.onFailedToPerformValidation(message);
    }
  }

  /**
   * A catch-all handler for managing problems encountered while executing other validations
   *
   * @param message An error message
   * @param error The error encountered
   */
  public void handleValidationFailure(final String message, Throwable error) {
    for (WeakSubjectivityViolationPolicy policy : violationPolicies) {
      policy.onFailedToPerformValidation(message, error);
    }
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

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final WeakSubjectivityValidator that = (WeakSubjectivityValidator) o;
    return Objects.equals(calculator, that.calculator)
        && Objects.equals(violationPolicies, that.violationPolicies)
        && Objects.equals(config, that.config);
  }

  @Override
  public int hashCode() {
    return Objects.hash(calculator, violationPolicies, config);
  }
}
