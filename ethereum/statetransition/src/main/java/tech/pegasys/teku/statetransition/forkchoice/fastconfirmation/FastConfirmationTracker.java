/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class FastConfirmationTracker {
  private static final Logger LOG = LogManager.getLogger();

  public static final FastConfirmationTracker NOOP =
      new FastConfirmationTracker(false, null, Optional.empty());

  private final boolean enabled;
  private final Spec spec;
  private final AtomicReference<FastConfirmationStore> fastConfirmationStore =
      new AtomicReference<>();

  /**
   * Slot of the most recently applied update. Guards against stale or duplicate async tasks
   * clobbering a newer store: {@code update_fast_confirmation_variables} MUST run exactly once per
   * slot and slot heads rotate in slot order, so an update is applied only when its slot is
   * strictly greater than this value. {@code null} until the first update after (re)initialization.
   */
  private final AtomicReference<UInt64> lastProcessedSlot = new AtomicReference<>();

  private final Optional<AsyncRunner> asyncRunner;

  private FastConfirmationTracker(
      final boolean enabled, final Spec spec, final Optional<AsyncRunner> asyncRunner) {
    this.enabled = enabled;
    this.spec = spec;
    this.asyncRunner = asyncRunner;
  }

  public static FastConfirmationTracker create(
      final Spec spec, final Optional<AsyncRunner> asyncRunner) {
    return new FastConfirmationTracker(true, spec, asyncRunner);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void initialize(final ReadOnlyStore store) {
    if (!enabled) {
      return;
    }
    lastProcessedSlot.set(null);
    fastConfirmationStore.set(FastConfirmationStore.create(store));
  }

  /**
   * Hands off the per-slot head snapshot to the dedicated runner. Only the head root is captured on
   * the (latency-critical) fork-choice thread; the epoch-boundary flags, greatest unrealized
   * justified checkpoint, source states and the confirmation computation are all derived on the
   * runner.
   */
  public SafeFuture<Void> onSlot(final UInt64 slot, final Bytes32 headRoot) {
    if (!enabled) {
      return SafeFuture.COMPLETE;
    }

    if (fastConfirmationStore.get() == null) {
      LOG.debug("Skipping fast confirmation update because store is not initialized");
      return SafeFuture.COMPLETE;
    }

    if (asyncRunner.isEmpty()) {
      LOG.warn("Skipping fast confirmation update because no async runner is configured");
      return SafeFuture.COMPLETE;
    }

    final FastConfirmationInput input = new FastConfirmationInput(slot, headRoot);
    final Duration updateTimeout = updateTimeout(slot);
    return asyncRunner
        .orElseThrow()
        .runAsync(() -> processFastConfirmationInput(input))
        .orTimeout(updateTimeout)
        .exceptionallyCompose(
            error -> {
              // A timeout means the side computation is lagging (e.g. slow state retrieval): log it
              // and move on rather than failing the fork-choice tick. Real errors still propagate.
              if (ExceptionUtil.hasCause(error, TimeoutException.class)) {
                LOG.warn(
                    "Fast confirmation update for slot {} timed out after {}", slot, updateTimeout);
                return SafeFuture.COMPLETE;
              }
              return SafeFuture.failedFuture(error);
            });
  }

  /**
   * Upper bound for a single per-slot update: half a slot. The confirmation computation should
   * finish well within a slot; this only guards against a hung or very slow state retrieval
   * monopolizing the dedicated single-thread runner. Applied via the default scheduler so it fires
   * independently of that runner thread.
   */
  private Duration updateTimeout(final UInt64 slot) {
    return Duration.ofMillis(spec.getSlotDurationMillis(slot) / 2L);
  }

  private void processFastConfirmationInput(final FastConfirmationInput input) {
    final FastConfirmationStore currentStore = fastConfirmationStore.get();
    if (currentStore == null) {
      LOG.debug("Skipping fast confirmation update because store is not initialized");
      return;
    }

    // Drop stale or duplicate updates. Tasks run on a dedicated single-thread runner so this
    // read-modify-write is serialized; the guard additionally ensures that an out-of-order or
    // repeated slot never overwrites a newer store (and enforces the spec's once-per-slot rule for
    // update_fast_confirmation_variables).
    final UInt64 lastSlot = lastProcessedSlot.get();
    if (lastSlot != null && input.slot().isLessThanOrEqualTo(lastSlot)) {
      LOG.debug(
          "Skipping fast confirmation update for slot {}: already processed slot {}",
          input.slot(),
          lastSlot);
      return;
    }

    // Derived off the fork-choice thread. The greatest unrealized justified checkpoint is only
    // needed on the last slot of an epoch (when the next slot starts a new epoch); otherwise it is
    // left at the finalized checkpoint and unused by update_fast_confirmation_variables.
    final boolean currentSlotIsEpochStart =
        FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, input.slot());
    final boolean nextSlotIsEpochStart =
        FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, input.slot().plus(1));
    final ReadOnlyStore store = currentStore.store();
    final Checkpoint greatestUnrealizedJustifiedCheckpoint =
        nextSlotIsEpochStart
            ? FastConfirmationRuleUtil.getGreatestUnrealizedJustifiedCheckpoint(store)
            : store.getFinalizedCheckpoint();

    final FastConfirmationStore withUpdatedVariables =
        FastConfirmationRuleUtil.updateFastConfirmationVariables(
            currentStore,
            input.headRoot(),
            greatestUnrealizedJustifiedCheckpoint,
            currentSlotIsEpochStart,
            nextSlotIsEpochStart);

    // on_fast_confirmation: fcr_store.confirmed_root = get_latest_confirmed(fcr_store).
    final FastConfirmationStore updatedStore =
        updateConfirmedRoot(withUpdatedVariables, input.slot(), currentSlotIsEpochStart);
    fastConfirmationStore.set(updatedStore);
    lastProcessedSlot.set(input.slot());

    LOG.info(
        "Fast confirmation update for slot {}: head={}, confirmed_root={}",
        input.slot(),
        input.headRoot(),
        updatedStore.confirmedRoot());
  }

  /**
   * Runs {@code get_latest_confirmed} against the loaded source states. The state loads are
   * composed concurrently and joined here; in practice the required states are cache-resident (the
   * head state and the justified checkpoint state), so the join is effectively non-blocking, and
   * the overall task is bounded by {@link #updateTimeout}. When a required state is unavailable the
   * confirmed root is left unchanged for this slot.
   */
  private FastConfirmationStore updateConfirmedRoot(
      final FastConfirmationStore fcrStore, final UInt64 slot, final boolean atEpochStart) {
    final Optional<FastConfirmationStates> maybeStates =
        FastConfirmationStateLoader.load(fcrStore, fcrStore.currentSlotHead(), atEpochStart).join();
    if (maybeStates.isEmpty()) {
      LOG.debug(
          "Skipping fast confirmation computation for slot {}: source states unavailable", slot);
      return fcrStore;
    }
    final Bytes32 confirmedRoot =
        new FastConfirmationCalculator(spec, fcrStore, maybeStates.get(), slot)
            .getLatestConfirmed();
    return fcrStore.withConfirmedRoot(confirmedRoot);
  }

  public Optional<FastConfirmationStore> getFastConfirmationStore() {
    return Optional.ofNullable(fastConfirmationStore.get());
  }
}
