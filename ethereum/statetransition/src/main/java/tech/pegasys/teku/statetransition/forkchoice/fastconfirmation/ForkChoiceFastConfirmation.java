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

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public final class ForkChoiceFastConfirmation {
  private static final Logger LOG = LogManager.getLogger();

  private ForkChoiceFastConfirmation() {}

  /**
   * Runs the FCR slot-start update after deferred attestations have been applied.
   *
   * <p>FCR variables are defined from the post-deferred-attestation head at slot start, so this
   * deliberately triggers the extra early-slot fork-choice calculation supplied by {@code
   * processHead}.
   */
  public static void processForSlot(
      final Spec spec,
      final RecentChainData recentChainData,
      final FastConfirmationTracker fastConfirmationTracker,
      final UInt64 currentSlot,
      final SafeFuture<Void> deferredAttestationsFuture,
      final Function<UInt64, SafeFuture<Optional<ChainHead>>> processHead) {
    deferredAttestationsFuture
        .thenCompose(__ -> processHead.apply(currentSlot))
        .thenCompose(
            maybeHead ->
                maybeHead
                    .map(
                        head ->
                            processHeadUpdate(
                                spec,
                                recentChainData,
                                fastConfirmationTracker,
                                currentSlot,
                                head.getRoot()))
                    .orElse(SafeFuture.COMPLETE))
        .finish(error -> LOG.error("Fast confirmation update failed", error));
  }

  /**
   * Snapshots the fork-choice data needed by {@code update_fast_confirmation_variables} before the
   * expensive side-runner work starts.
   */
  private static SafeFuture<Void> processHeadUpdate(
      final Spec spec,
      final RecentChainData recentChainData,
      final FastConfirmationTracker fastConfirmationTracker,
      final UInt64 currentSlot,
      final Bytes32 headRoot) {
    final boolean currentSlotIsEpochStart =
        FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, currentSlot);
    final boolean nextSlotIsEpochStart =
        FastConfirmationRuleUtil.isStartSlotAtEpoch(spec, currentSlot.plus(1));
    final Checkpoint greatestUnrealizedJustifiedCheckpoint =
        nextSlotIsEpochStart
            ? getGreatestUnrealizedJustifiedCheckpoint(recentChainData)
            : recentChainData.getStore().getFinalizedCheckpoint();
    return fastConfirmationTracker.onSlotHeadUpdated(
        currentSlot,
        headRoot,
        greatestUnrealizedJustifiedCheckpoint,
        currentSlotIsEpochStart,
        nextSlotIsEpochStart);
  }

  /**
   * Reconstructs {@code store.unrealized_justified_checkpoint} from Teku's per-block checkpoint
   * metadata.
   */
  private static Checkpoint getGreatestUnrealizedJustifiedCheckpoint(
      final RecentChainData recentChainData) {
    final ReadOnlyStore store = recentChainData.getStore();
    return store.getForkChoiceStrategy().getBlockData().stream()
        .map(ProtoNodeData::getCheckpoints)
        .map(BlockCheckpoints::getUnrealizedJustifiedCheckpoint)
        .max(Comparator.comparing(Checkpoint::getEpoch))
        .orElseGet(store::getFinalizedCheckpoint);
  }
}
