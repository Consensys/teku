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

package tech.pegasys.teku.sync.forward.multipeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.sync.forward.multipeer.ChainSelector.MIN_PENDING_BLOCKS;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChains;

class ChainSelectorTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  @SuppressWarnings("unchecked")
  private final PendingPool<SignedBeaconBlock> pendingBlocks = mock(PendingPool.class);

  private final TargetChains targetChains = new TargetChains();

  private final TargetChain targetChain =
      new TargetChain(dataStructureUtil.randomSlotAndBlockRoot());

  private final ChainSelector forkChainSelector =
      ChainSelector.createForForkChains(recentChainData, targetChains, pendingBlocks);

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
    assertThat(
            ChainSelector.createForCanonicalChains(recentChainData, targetChains)
                .selectTargetChain(Optional.of(targetChain)))
        .isPresent();
  }

  @Test
  void shouldNotSelectChainsThatAreNotAheadWhenSyncAlreadyInProgress() {
    addPeerAtSlot(recentChainData.getHeadSlot());
    assertThat(
            ChainSelector.createForCanonicalChains(recentChainData, targetChains)
                .selectTargetChain(Optional.of(targetChain)))
        .isEmpty();
  }

  @Test
  void shouldSwitchToChainWithMorePeersWhenAlreadySyncing() {
    final ChainSelector chainSelector =
        ChainSelector.createForCanonicalChains(recentChainData, targetChains);
    final UInt64 remoteHeadSlot = recentChainData.getHeadSlot().plus(SLOTS_PER_EPOCH + 1);
    final SlotAndBlockRoot chainHead1 = addPeerAtSlot(remoteHeadSlot);
    final SlotAndBlockRoot chainHead2 = addPeerAtSlot(remoteHeadSlot.plus(5));
    addPeerToChain(chainHead1);
    addPeerToChain(chainHead1);

    // Then chain 2 comes along with a later head block but only the same number of peers
    addPeerToChain(chainHead2);
    addPeerToChain(chainHead2);

    final TargetChain targetChain1 = getTargetChain(chainHead1);
    // We're already syncing chain1 and should continue that even though chain2 now has a later head
    // to avoid thrashing between chains as the status is updated
    assertThat(chainSelector.selectTargetChain(Optional.of(targetChain1)))
        .isPresent()
        .map(TargetChain::getChainHead)
        .contains(chainHead1);

    // But once chain 2 has more peers, we should switch to it
    addPeerToChain(chainHead2);
    assertThat(chainSelector.selectTargetChain(Optional.of(targetChain1)))
        .isPresent()
        .map(TargetChain::getChainHead)
        .contains(chainHead2);
  }

  @Test
  void shouldNotSyncToChainBeyondCurrentHeadWhenHeadBlockAlreadyImported() {
    // Covers the case where we have fully sync'd a chain but attestation weight means that we
    // consider a chain with a lower head block canonical. The fork may then pass the checks that
    // it's head is far enough past our head block, but should still be excluded as a sync target
    // because we already have it
    final UInt64 remoteHeadSlot = recentChainData.getHeadSlot().plus(SLOTS_PER_EPOCH + 1);
    final SlotAndBlockRoot chainHead1 = addPeerAtSlot(remoteHeadSlot);
    when(recentChainData.containsBlock(chainHead1.getBlockRoot())).thenReturn(true);

    addPeerToChain(chainHead1);
    addPeerToChain(chainHead1);

    assertThat(selectSyncTarget()).isEmpty();
  }

  @Test
  void forkSelector_shouldNotSelectTargetChainWhenHeadAlreadyImported() {
    final SlotAndBlockRoot headBlock = addPeerAtSlot(recentChainData.getHeadSlot().plus(200));
    when(recentChainData.containsBlock(headBlock.getBlockRoot())).thenReturn(true);

    assertThat(forkChainSelector.selectTargetChain(Optional.empty())).isEmpty();
  }

  @Test
  void forkSelector_shouldSelectTargetChainWhenEnoughBlocksWaitingInPendingPool() {
    final SlotAndBlockRoot chainHead =
        addPendingBlocks(recentChainData.getHeadSlot(), MIN_PENDING_BLOCKS);
    addPeerToChain(chainHead);

    assertThat(forkChainSelector.selectTargetChain(Optional.empty()))
        .isPresent()
        .map(TargetChain::getChainHead)
        .contains(chainHead);
  }

  @Test
  void forkSelector_shouldNotSelectTargetWhenNotEnoughBlocksArePending() {
    final SlotAndBlockRoot chainHead =
        addPendingBlocks(recentChainData.getHeadSlot(), MIN_PENDING_BLOCKS - 1);
    addPeerToChain(chainHead);

    assertThat(forkChainSelector.selectTargetChain(Optional.empty())).isEmpty();
  }

  private SlotAndBlockRoot addPendingBlocks(
      final UInt64 headBlockSlot, final int numberOfPendingBlocks) {
    // +1 because the block count is inclusive of the earliest and head blocks
    final long earliestBlockSlot = headBlockSlot.longValue() - numberOfPendingBlocks + 1;
    SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(earliestBlockSlot - 1);
    for (long slot = earliestBlockSlot; slot <= headBlockSlot.longValue(); slot++) {
      block = dataStructureUtil.randomSignedBeaconBlock(slot, block.getRoot());
      when(pendingBlocks.get(block.getRoot())).thenReturn(Optional.of(block));
    }
    return new SlotAndBlockRoot(block.getSlot(), block.getRoot());
  }

  private TargetChain getTargetChain(final SlotAndBlockRoot chainHead1) {
    return targetChains
        .streamChains()
        .filter(chain -> chain.getChainHead().equals(chainHead1))
        .findFirst()
        .orElseThrow();
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
    return ChainSelector.createForCanonicalChains(recentChainData, targetChains)
        .selectTargetChain(Optional.empty());
  }
}
