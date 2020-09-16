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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.storage.client.RecentChainData;

class FinalizedTargetChainSelectorTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final TargetChains targetChains = new TargetChains();

  private final FinalizedTargetChainSelector selector =
      new FinalizedTargetChainSelector(recentChainData);

  @BeforeEach
  void setUp() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.valueOf(50));
  }

  @Test
  void shouldNotSelectAChainWhereThereAreNoneToSelect() {
    assertThat(selector.selectTargetChain(targetChains)).isEmpty();
  }

  @Test
  void shouldNotSelectTargetChainsLessThanCurrentFinalizedEpoch() {
    addPeerAtSlot(UInt64.ONE);

    assertThat(selector.selectTargetChain(targetChains)).isEmpty();
  }

  @Test
  void shouldNotSelectTargetChainsWithSameFinalizedEpoch() {
    addPeerAtSlot(recentChainData.getFinalizedEpoch());

    assertThat(selector.selectTargetChain(targetChains)).isEmpty();
  }

  @Test
  void shouldNotSelectTargetChainsWithNextFinalizedEpoch() {
    // Avoids triggering a sync when we may be just about to finalize the next epoch anyway
    addPeerAtSlot(recentChainData.getFinalizedEpoch().plus(1));

    assertThat(selector.selectTargetChain(targetChains)).isEmpty();
  }

  @Test
  void shouldSelectSuitableTargetChainWithMostPeers() {
    // Avoids triggering a sync when we may be just about to finalize the next epoch anyway
    final SlotAndBlockRoot chainHead1 = addPeerAtSlot(recentChainData.getFinalizedEpoch().plus(2));
    final SlotAndBlockRoot chainHead2 = addPeerAtSlot(recentChainData.getFinalizedEpoch().plus(2));
    addPeerToChain(chainHead1);
    addPeerToChain(chainHead1);
    addPeerToChain(chainHead2);

    assertThat(selector.selectTargetChain(targetChains))
        .isPresent()
        .map(TargetChain::getChainHead)
        .contains(chainHead1);
  }

  private SlotAndBlockRoot addPeerAtSlot(final UInt64 epoch) {
    final SlotAndBlockRoot chainHead =
        new SlotAndBlockRoot(compute_start_slot_at_epoch(epoch), dataStructureUtil.randomBytes32());
    addPeerToChain(chainHead);
    return chainHead;
  }

  private void addPeerToChain(final SlotAndBlockRoot chainHead) {
    targetChains.onPeerStatusUpdated(mock(SyncSource.class), chainHead);
  }
}
