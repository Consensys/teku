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

package tech.pegasys.teku.statetransition;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.BeaconStateUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class EpochCachePrimer {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;

  public EpochCachePrimer(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public void primeCacheForEpoch(final UInt64 epoch) {
    final UInt64 firstSlot = spec.computeStartSlotAtEpoch(epoch);
    recentChainData
        .getHeadBlock()
        // Don't preprocess epoch if we're more than an epoch behind as we likely need to sync
        .filter(
            headBlock ->
                isWithinOneEpochOfHeadBlock(firstSlot, headBlock)
                    && isAfterHeadBlockEpoch(epoch, headBlock))
        .ifPresent(
            headBlock ->
                recentChainData
                    .retrieveStateAtSlot(new SlotAndBlockRoot(firstSlot, headBlock.getRoot()))
                    .finish(
                        maybeState -> maybeState.ifPresent(this::primeEpochStateCaches),
                        error -> LOG.warn("Failed to precompute epoch transition", error)));
  }

  private boolean isWithinOneEpochOfHeadBlock(
      final UInt64 firstSlot, final SignedBeaconBlock block) {
    return block.getSlot().plus(spec.getSlotsPerEpoch(firstSlot)).isGreaterThanOrEqualTo(firstSlot);
  }

  private boolean isAfterHeadBlockEpoch(final UInt64 epoch, final SignedBeaconBlock headBlock) {
    return spec.computeEpochAtSlot(headBlock.getSlot()).isLessThan(epoch);
  }

  private void primeEpochStateCaches(final BeaconState state) {
    UInt64.range(state.getSlot(), state.getSlot().plus(spec.getSlotsPerEpoch(state.getSlot())))
        .forEach(
            slot -> {
              final BeaconStateUtil beaconStateUtil = spec.getBeaconStateUtil(state.getSlot());
              // Calculate all proposers
              spec.getBeaconProposerIndex(state, slot);

              // Calculate attesters total effective balance
              beaconStateUtil.getAttestersTotalEffectiveBalance(state, slot);
            });

    // Calculate committees for max lookahead epoch
    // (assume earlier epochs were already requested)
    final UInt64 lookaheadEpoch = spec.getMaxLookaheadEpoch(state);
    final UInt64 lookAheadEpochStartSlot = spec.computeStartSlotAtEpoch(lookaheadEpoch);
    final UInt64 committeeCount = spec.getCommitteeCountPerSlot(state, lookaheadEpoch);
    UInt64.range(lookAheadEpochStartSlot, spec.computeStartSlotAtEpoch(lookaheadEpoch.plus(1)))
        .forEach(
            slot ->
                UInt64.range(UInt64.ZERO, committeeCount)
                    .forEach(
                        index ->
                            spec.getBeaconStateUtil(slot).getBeaconCommittee(state, slot, index)));
  }
}
