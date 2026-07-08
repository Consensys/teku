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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class FastConfirmationTracker {
  private static final Logger LOG = LogManager.getLogger();

  public static final FastConfirmationTracker NOOP =
      new FastConfirmationTracker(false, Optional.empty());

  private final boolean enabled;
  private final AtomicReference<FastConfirmationStore> fastConfirmationStore =
      new AtomicReference<>();
  private final Optional<AsyncRunner> asyncRunner;

  private FastConfirmationTracker(final boolean enabled, final Optional<AsyncRunner> asyncRunner) {
    this.enabled = enabled;
    this.asyncRunner = asyncRunner;
  }

  public static FastConfirmationTracker create(final Optional<AsyncRunner> asyncRunner) {
    return new FastConfirmationTracker(true, asyncRunner);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void initialize(final ReadOnlyStore store) {
    if (!enabled) {
      return;
    }
    fastConfirmationStore.set(FastConfirmationStore.create(store));
  }

  public SafeFuture<Void> onSlotHeadUpdated(
      final UInt64 slot,
      final Bytes32 headRoot,
      final Checkpoint greatestUnrealizedJustifiedCheckpoint,
      final boolean currentSlotIsEpochStart,
      final boolean nextSlotIsEpochStart) {
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

    final FastConfirmationInput input =
        new FastConfirmationInput(
            slot,
            headRoot,
            greatestUnrealizedJustifiedCheckpoint,
            currentSlotIsEpochStart,
            nextSlotIsEpochStart);
    return asyncRunner.orElseThrow().runAsync(() -> processFastConfirmationInput(input));
  }

  private void processFastConfirmationInput(final FastConfirmationInput input) {
    final FastConfirmationStore currentStore = fastConfirmationStore.get();
    if (currentStore == null) {
      LOG.debug("Skipping fast confirmation update because store is not initialized");
      return;
    }

    final FastConfirmationStore updatedStore =
        FastConfirmationRuleUtil.updateFastConfirmationVariablesFromInput(currentStore, input);
    fastConfirmationStore.set(updatedStore);

    LOG.info(
        "Fast confirmation update for slot {}: head={}, confirmed_root={}",
        input.slot(),
        input.headRoot(),
        updatedStore.confirmedRoot());
  }

  public Optional<FastConfirmationStore> getFastConfirmationStore() {
    return Optional.ofNullable(fastConfirmationStore.get());
  }
}
