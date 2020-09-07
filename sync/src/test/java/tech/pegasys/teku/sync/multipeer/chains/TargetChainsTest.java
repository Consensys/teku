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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.network.p2p.peer.StubPeer;
import tech.pegasys.teku.networking.p2p.peer.Peer;

class TargetChainsTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final TargetChains<Peer> targetChains = new TargetChains<>();
  private final StubPeer peer1 = new StubPeer(1);
  private final StubPeer peer2 = new StubPeer(2);

  @Test
  void onPeerStatus_shouldAddNewChainToTrack() {
    final SlotAndBlockRoot chainHead = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead);

    assertTargetChains(chainWith(chainHead, peer1));
  }

  @Test
  void onPeerStatus_shouldAddPeerToExistingChain() {
    final SlotAndBlockRoot chainHead = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead);
    targetChains.onPeerStatusUpdated(peer2, chainHead);

    assertTargetChains(chainWith(chainHead, peer1, peer2));
  }

  @Test
  void onPeerStatus_shouldAddPeerToDifferentChain() {
    final SlotAndBlockRoot chainHead1 = dataStructureUtil.randomSlotAndBlockRoot();
    final SlotAndBlockRoot chainHead2 = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead1);
    targetChains.onPeerStatusUpdated(peer2, chainHead2);

    assertTargetChains(chainWith(chainHead1, peer1), chainWith(chainHead2, peer2));
  }

  @Test
  void onPeerStatus_shouldMovePeerToDifferentChainWhenStatusChanges() {
    final SlotAndBlockRoot chainHead1 = dataStructureUtil.randomSlotAndBlockRoot();
    final SlotAndBlockRoot chainHead2 = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead1);
    targetChains.onPeerStatusUpdated(peer2, chainHead1);

    // Then peer 1 changes
    targetChains.onPeerStatusUpdated(peer1, chainHead2);

    assertTargetChains(chainWith(chainHead1, peer2), chainWith(chainHead2, peer1));
  }

  @Test
  void onPeerStatus_shouldRemoveChainWhenLastPeerChangesToADifferentChain() {
    final SlotAndBlockRoot chainHead1 = dataStructureUtil.randomSlotAndBlockRoot();
    final SlotAndBlockRoot chainHead2 = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead1);
    targetChains.onPeerStatusUpdated(peer1, chainHead2);

    assertTargetChains(chainWith(chainHead2, peer1));
  }

  @Test
  void onPeerDisconnected_shouldRemovePeerFromChainWhenDisconnected() {
    final SlotAndBlockRoot chainHead1 = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead1);
    targetChains.onPeerStatusUpdated(peer2, chainHead1);

    // Then peer 1 disconnects
    targetChains.onPeerDisconnected(peer1);

    assertTargetChains(chainWith(chainHead1, peer2));
  }

  @Test
  void onPeerStatus_shouldRemoveChainWhenLastPeerDisconnects() {
    final SlotAndBlockRoot chainHead1 = dataStructureUtil.randomSlotAndBlockRoot();
    targetChains.onPeerStatusUpdated(peer1, chainHead1);

    targetChains.onPeerDisconnected(peer1);

    assertTargetChains();
  }

  @Test
  void shouldOrderChainsByPeerCountThenSlot() {
    final StubPeer peer3 = new StubPeer(3);
    final StubPeer peer4 = new StubPeer(4);
    final SlotAndBlockRoot chainHead1 =
        new SlotAndBlockRoot(UInt64.valueOf(100), dataStructureUtil.randomBytes32());
    final SlotAndBlockRoot chainHead2 =
        new SlotAndBlockRoot(UInt64.valueOf(100), dataStructureUtil.randomBytes32());
    final SlotAndBlockRoot chainHead3 =
        new SlotAndBlockRoot(UInt64.valueOf(102), dataStructureUtil.randomBytes32());

    targetChains.onPeerStatusUpdated(peer1, chainHead1);
    targetChains.onPeerStatusUpdated(peer2, chainHead1);
    targetChains.onPeerStatusUpdated(peer3, chainHead2);
    targetChains.onPeerStatusUpdated(peer4, chainHead3);

    assertThat(targetChains.streamChains())
        .containsExactly(
            chainWith(chainHead1, peer1, peer2),
            chainWith(chainHead3, peer4),
            chainWith(chainHead2, peer3));
  }

  @SafeVarargs
  private void assertTargetChains(final TargetChain<Peer>... expected) {
    assertThat(targetChains.streamChains()).containsExactlyInAnyOrder(expected);
  }

  private TargetChain<Peer> chainWith(final SlotAndBlockRoot chainHead, final Peer... peers) {
    final TargetChain<Peer> chain = new TargetChain<>(chainHead);
    Stream.of(peers).forEach(chain::addPeer);
    return chain;
  }
}
