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

package tech.pegasys.teku.sync.forward.multipeer.chains;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.forward.multipeer.ChainSelector;

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
  void shouldNotSelectAChainWhereThereAreNoneToSelect() {
    assertThat(selectSyncTarget()).isEmpty();
  }

  @Test
  void shouldNotSelectTargetChainsLessThanCurrentHeadSlot() {
    addPeerAtSlot(UInt64.ONE);

    assertThat(selectSyncTarget()).isEmpty();
  }

  @Test
  void shouldNotSelectTargetChainsWithSameHeadSlot() {
    addPeerAtSlot(recentChainData.getHeadSlot());

    assertThat(selectSyncTarget()).isEmpty();
  }

  @Test
  void shouldNotSelectTargetChainsWithinToleranceOfOurHeadSlot() {
    addPeerAtSlot(recentChainData.getHeadSlot().plus(SLOTS_PER_EPOCH));

    assertThat(selectSyncTarget()).isEmpty();
  }

  @Test
  void shouldSelectSuitableTargetChainWithMostPeers() {
    // Avoids triggering a sync when we may be just about to finalize the next epoch anyway
    final UInt64 remoteHeadSlot = recentChainData.getHeadSlot().plus(SLOTS_PER_EPOCH + 1);
    final SlotAndBlockRoot chainHead1 = addPeerAtSlot(remoteHeadSlot);
    final SlotAndBlockRoot chainHead2 = addPeerAtSlot(remoteHeadSlot);
    addPeerToChain(chainHead1);
    addPeerToChain(chainHead1);
    addPeerToChain(chainHead2);

    assertThat(selectSyncTarget()).isPresent().map(TargetChain::getChainHead).contains(chainHead1);
  }

  @Test
  void shouldNotAddToleranceWhenSyncAlreadyInProgress() {
    addPeerAtSlot(recentChainData.getHeadSlot().plus(1));
    assertThat(new ChainSelector(recentChainData, targetChains).selectTargetChain(true))
        .isPresent();
  }

  @Test
  void shouldNotSelectChainsThatAreNotAheadWhenSyncAlreadyInProgress() {
    addPeerAtSlot(recentChainData.getHeadSlot());
    assertThat(new ChainSelector(recentChainData, targetChains).selectTargetChain(true)).isEmpty();
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

  private Optional<TargetChain> selectSyncTarget() {
    return new ChainSelector(recentChainData, targetChains).selectTargetChain(false);
  }
}
