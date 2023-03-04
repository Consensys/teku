/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_GOSSIP_CLOCK_DISPARITY;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipValidationHelper {
  public static final UInt64 MAX_OFFSET_TIME_IN_SECONDS =
      UInt64.valueOf(Math.round((float) MAXIMUM_GOSSIP_CLOCK_DISPARITY / 1000.0));

  private final Spec spec;
  private final RecentChainData recentChainData;

  public GossipValidationHelper(final Spec spec, RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public boolean isSlotFinalized(final UInt64 slot) {
    final UInt64 finalizedSlot =
        recentChainData.getStore().getFinalizedCheckpoint().getEpochStartSlot(spec);
    return slot.isLessThanOrEqualTo(finalizedSlot);
  }

  public boolean isSlotFromFuture(final UInt64 slot) {
    final ReadOnlyStore store = recentChainData.getStore();
    final UInt64 maxTime = store.getTimeSeconds().plus(MAX_OFFSET_TIME_IN_SECONDS);
    final UInt64 maxCurrSlot = spec.getCurrentSlot(maxTime, store.getGenesisTime());
    return slot.isGreaterThan(maxCurrSlot);
  }

  public boolean isSignatureValidWithRespectToProposerIndex(
      final Bytes signingRoot,
      final UInt64 proposerIndex,
      final BLSSignature signature,
      final BeaconState postState) {

    return spec.getValidatorPubKey(postState, proposerIndex)
        .map(publicKey -> BLS.verify(publicKey, signingRoot, signature))
        .orElse(false);
  }

  /**
   * Retrieve the state for the parent block, applying the epoch transition if required to be able
   * to calculate the expected proposer for block.
   */
  public SafeFuture<Optional<BeaconState>> getParentStateInBlockEpoch(
      final UInt64 parentBlockSlot, final Bytes32 parentBlockRoot, final UInt64 slot) {
    final UInt64 firstSlotInBlockEpoch =
        spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(slot));
    return parentBlockSlot.isLessThan(firstSlotInBlockEpoch)
        ? recentChainData.retrieveStateAtSlot(
            new SlotAndBlockRoot(firstSlotInBlockEpoch, parentBlockRoot))
        : recentChainData.retrieveBlockState(parentBlockRoot);
  }

  public boolean isProposerTheExpectedProposer(
      final UInt64 proposerIndex, final UInt64 slot, final BeaconState postState) {
    final int expectedProposerIndex = spec.getBeaconProposerIndex(postState, slot);
    return expectedProposerIndex == proposerIndex.longValue();
  }

  public Optional<UInt64> getSlotForBlockRoot(final Bytes32 blockRoot) {
    return recentChainData.getSlotForBlockRoot(blockRoot);
  }

  public boolean isBlockAvailable(final Bytes32 blockRoot) {
    return recentChainData.containsBlock(blockRoot);
  }
}
