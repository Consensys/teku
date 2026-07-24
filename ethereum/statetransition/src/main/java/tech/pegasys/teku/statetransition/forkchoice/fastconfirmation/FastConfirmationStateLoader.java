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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * Retrieves the source states the Fast Confirmation Rule needs for a slot.
 *
 * <p>The three retrievals run concurrently on the store's own runners and are composed
 * asynchronously, so no state load ever blocks the (single-thread) fast confirmation runner. If any
 * required state is unavailable (e.g. pruned), the result is empty and the caller skips the slot.
 */
final class FastConfirmationStateLoader {

  private FastConfirmationStateLoader() {}

  /**
   * @param includePreviousBalanceSource whether to also retrieve {@code
   *     get_previous_balance_source}. It is only read by reconfirmation, which runs at epoch-start
   *     slots, so callers pass {@code is_start_slot_at_epoch(currentSlot)} to avoid an unnecessary
   *     (and often uncached) fetch of the previous epoch's checkpoint state on other slots.
   */
  static SafeFuture<Optional<FastConfirmationStates>> load(
      final FastConfirmationStore fcrStore,
      final Bytes32 head,
      final boolean includePreviousBalanceSource) {
    final ReadOnlyStore store = fcrStore.store();
    final SafeFuture<Optional<BeaconState>> previousBalanceSource =
        includePreviousBalanceSource
            ? store.retrieveCheckpointState(fcrStore.previousEpochObservedJustifiedCheckpoint())
            : SafeFuture.completedFuture(Optional.empty());
    final SafeFuture<Optional<BeaconState>> currentBalanceSource =
        store.retrieveCheckpointState(fcrStore.currentEpochObservedJustifiedCheckpoint());
    final SafeFuture<Optional<BeaconState>> headBlockState = store.retrieveBlockState(head);

    // The futures are already running; compose (rather than join) so the runner is not blocked
    // while they complete.
    return previousBalanceSource.thenCompose(
        previous ->
            currentBalanceSource.thenCompose(
                current ->
                    headBlockState.thenApply(
                        headState ->
                            combine(includePreviousBalanceSource, previous, current, headState))));
  }

  private static Optional<FastConfirmationStates> combine(
      final boolean includePreviousBalanceSource,
      final Optional<BeaconState> previousBalanceSource,
      final Optional<BeaconState> currentBalanceSource,
      final Optional<BeaconState> headBlockState) {
    // The mandatory sources must be present; the previous balance source only when requested.
    if (currentBalanceSource.isEmpty()
        || headBlockState.isEmpty()
        || (includePreviousBalanceSource && previousBalanceSource.isEmpty())) {
      return Optional.empty();
    }
    return Optional.of(
        new FastConfirmationStates(
            previousBalanceSource, currentBalanceSource.get(), headBlockState.get()));
  }
}
