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

package tech.pegasys.teku.sync.multipeer.chains;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.sync.multipeer.ChainSelector.createFinalizedChainSelector;
import static tech.pegasys.teku.sync.multipeer.ChainSelector.createNonfinalizedChainSelector;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.storage.client.RecentChainData;

class ChainSelectorTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final TargetChains targetChains = new TargetChains();

  @BeforeEach
  void setUp() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.valueOf(50));
    when(recentChainData.getHeadSlot()).thenReturn(UInt64.valueOf(500));
  }

  @Test
  void finalized_shouldNotSelectAChainWhereThereAreNoneToSelect() {
    assertThat(selectFinalSyncTarget()).isEmpty();
  }

  @Test
  void finalized_shouldNotSelectTargetChainsLessThanCurrentFinalizedEpoch() {
    addPeerAtEpoch(UInt64.ONE);

    assertThat(selectFinalSyncTarget()).isEmpty();
  }

  @Test
  void finalized_shouldNotSelectTargetChainsWithSameFinalizedEpoch() {
    addPeerAtEpoch(recentChainData.getFinalizedEpoch());

    assertThat(selectFinalSyncTarget()).isEmpty();
  }

  @Test
  void finalized_shouldNotSelectTargetChainsWithNextFinalizedEpoch() {
    // Avoids triggering a sync when we may be just about to finalize the next epoch anyway
    addPeerAtEpoch(recentChainData.getFinalizedEpoch().plus(1));

    assertThat(selectFinalSyncTarget()).isEmpty();
  }

  @Test
  void finalized_shouldSelectSuitableTargetChainWithMostPeers() {
    // Avoids triggering a sync when we may be just about to finalize the next epoch anyway
    final SlotAndBlockRoot chainHead1 = addPeerAtEpoch(recentChainData.getFinalizedEpoch().plus(2));
    final SlotAndBlockRoot chainHead2 = addPeerAtEpoch(recentChainData.getFinalizedEpoch().plus(2));
    addPeerToChain(chainHead1);
    addPeerToChain(chainHead1);
    addPeerToChain(chainHead2);

    assertThat(selectFinalSyncTarget())
        .isPresent()
        .map(TargetChain::getChainHead)
        .contains(chainHead1);
  }

  @Test
  void finalized_shouldNotAddToleranceWhenSyncAlreadyInProgress() {
    addPeerAtEpoch(recentChainData.getFinalizedEpoch().plus(1));
    assertThat(createFinalizedChainSelector(recentChainData).selectTargetChain(targetChains, true))
        .isPresent();
  }

  @Test
  void finalized_shouldNotSelectChainsThatAreNotAheadWhenSyncAlreadyInProgress() {
    addPeerAtEpoch(recentChainData.getFinalizedEpoch());
    assertThat(createFinalizedChainSelector(recentChainData).selectTargetChain(targetChains, true))
        .isEmpty();
  }

  @Test
  void nonfinalized_shouldNotSelectAChainWhereThereAreNoneToSelect() {
    assertThat(selectNonfinalSyncTarget()).isEmpty();
  }

  @Test
  void nonfinalized_shouldNotSelectTargetChainsLessThanCurrentHeadSlot() {
    addPeerAtSlot(UInt64.ONE);

    assertThat(selectNonfinalSyncTarget()).isEmpty();
  }

  @Test
  void nonfinalized_shouldNotSelectTargetChainsWithSameHeadSlot() {
    addPeerAtSlot(recentChainData.getHeadSlot());

    assertThat(selectNonfinalSyncTarget()).isEmpty();
  }

  @Test
  void nonfinalized_shouldNotSelectTargetChainsWithinToleranceOfOurHeadSlot() {
    addPeerAtSlot(recentChainData.getHeadSlot().plus(SLOTS_PER_EPOCH));

    assertThat(selectNonfinalSyncTarget()).isEmpty();
  }

  @Test
  void nonfinalized_shouldSelectSuitableTargetChainWithMostPeers() {
    // Avoids triggering a sync when we may be just about to finalize the next epoch anyway
    final UInt64 remoteHeadSlot = recentChainData.getHeadSlot().plus(SLOTS_PER_EPOCH + 1);
    final SlotAndBlockRoot chainHead1 = addPeerAtSlot(remoteHeadSlot);
    final SlotAndBlockRoot chainHead2 = addPeerAtSlot(remoteHeadSlot);
    addPeerToChain(chainHead1);
    addPeerToChain(chainHead1);
    addPeerToChain(chainHead2);

    assertThat(selectNonfinalSyncTarget())
        .isPresent()
        .map(TargetChain::getChainHead)
        .contains(chainHead1);
  }

  @Test
  void nonfinalized_shouldNotAddToleranceWhenSyncAlreadyInProgress() {
    addPeerAtSlot(recentChainData.getHeadSlot().plus(1));
    assertThat(
            createNonfinalizedChainSelector(recentChainData).selectTargetChain(targetChains, true))
        .isPresent();
  }

  @Test
  void nonfinalized_shouldNotSelectChainsThatAreNotAheadWhenSyncAlreadyInProgress() {
    addPeerAtSlot(recentChainData.getHeadSlot());
    assertThat(
            createNonfinalizedChainSelector(recentChainData).selectTargetChain(targetChains, true))
        .isEmpty();
  }

  private SlotAndBlockRoot addPeerAtEpoch(final UInt64 epoch) {
    return addPeerAtSlot(compute_start_slot_at_epoch(epoch));
  }

  private SlotAndBlockRoot addPeerAtSlot(final UInt64 slot) {
    final SlotAndBlockRoot chainHead =
        new SlotAndBlockRoot(slot, dataStructureUtil.randomBytes32());
    addPeerToChain(chainHead);
    return chainHead;
  }

  private void addPeerToChain(final SlotAndBlockRoot chainHead) {
    targetChains.onPeerStatusUpdated(mock(SyncSource.class), chainHead);
  }

  private Optional<TargetChain> selectFinalSyncTarget() {
    return createFinalizedChainSelector(recentChainData).selectTargetChain(targetChains, false);
  }

  private Optional<TargetChain> selectNonfinalSyncTarget() {
    return createNonfinalizedChainSelector(recentChainData).selectTargetChain(targetChains, false);
  }
}
