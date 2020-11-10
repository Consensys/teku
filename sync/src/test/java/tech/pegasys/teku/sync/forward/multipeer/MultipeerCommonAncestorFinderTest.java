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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.sync.forward.multipeer.chains.TargetChainTestUtil.chainWith;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.StubSyncSource;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.forward.singlepeer.CommonAncestor;

class MultipeerCommonAncestorFinderTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final CommonAncestor commonAncestor = mock(CommonAncestor.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final InlineEventThread eventThread = new InlineEventThread();
  private final SyncSource syncSource1 = new StubSyncSource();
  private final SyncSource syncSource2 = new StubSyncSource();

  private final MultipeerCommonAncestorFinder commonAncestorFinder =
      new MultipeerCommonAncestorFinder(recentChainData, commonAncestor, eventThread);
  private static final UInt64 FINALIZED_EPOCH = UInt64.valueOf(10);
  private static final UInt64 FINALIZED_SLOT = compute_start_slot_at_epoch(FINALIZED_EPOCH);

  @BeforeEach
  void setUp() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(FINALIZED_EPOCH);
  }

  @Test
  void shouldReturnLatestFinalizedSlotWhenNoPeersAvailable() {
    final TargetChain chain =
        chainWith(new SlotAndBlockRoot(UInt64.valueOf(10_000), dataStructureUtil.randomBytes32()));

    final SafeFuture<UInt64> result = findCommonAncestor(chain);
    assertThat(result).isCompletedWithValue(FINALIZED_SLOT);
  }

  @Test
  void shouldFindCommonAncestorFromSingleSourceWhenOnlyOneSourceAvailable() {
    final TargetChain chain =
        chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(10_000), dataStructureUtil.randomBytes32()),
            syncSource1);
    final SafeFuture<UInt64> source1CommonAncestor = new SafeFuture<>();
    when(commonAncestor.getCommonAncestor(any(), any(), any())).thenReturn(source1CommonAncestor);

    final SafeFuture<UInt64> result = findCommonAncestor(chain);
    assertThat(result).isNotDone();

    verify(commonAncestor)
        .getCommonAncestor(syncSource1, FINALIZED_SLOT, chain.getChainHead().getSlot());

    final UInt64 expected = UInt64.valueOf(4243);
    source1CommonAncestor.complete(expected);
    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  void shouldFindCommonAncestorWhenMultipleSourcesAgree() {
    final TargetChain chain =
        chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(10_000), dataStructureUtil.randomBytes32()),
            syncSource1,
            syncSource2);
    final SafeFuture<UInt64> source1CommonAncestor = new SafeFuture<>();
    final SafeFuture<UInt64> source2CommonAncestor = new SafeFuture<>();
    when(commonAncestor.getCommonAncestor(any(), any(), any()))
        .thenReturn(source1CommonAncestor)
        .thenReturn(source2CommonAncestor);

    final SafeFuture<UInt64> result = findCommonAncestor(chain);
    assertThat(result).isNotDone();

    verify(commonAncestor)
        .getCommonAncestor(syncSource1, FINALIZED_SLOT, chain.getChainHead().getSlot());

    verify(commonAncestor)
        .getCommonAncestor(syncSource2, FINALIZED_SLOT, chain.getChainHead().getSlot());

    final UInt64 expected = UInt64.valueOf(4243);
    source1CommonAncestor.complete(expected);
    assertThat(result).isNotDone();

    source2CommonAncestor.complete(expected);
    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  void shouldUseLatestFinalizedSlotWhenMultipleSourcesDisagree() {
    final TargetChain chain =
        chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(10_000), dataStructureUtil.randomBytes32()),
            syncSource1,
            syncSource2);
    final SafeFuture<UInt64> source1CommonAncestor = new SafeFuture<>();
    final SafeFuture<UInt64> source2CommonAncestor = new SafeFuture<>();
    when(commonAncestor.getCommonAncestor(any(), any(), any()))
        .thenReturn(source1CommonAncestor)
        .thenReturn(source2CommonAncestor);

    final SafeFuture<UInt64> result = findCommonAncestor(chain);
    assertThat(result).isNotDone();

    verify(commonAncestor)
        .getCommonAncestor(syncSource1, FINALIZED_SLOT, chain.getChainHead().getSlot());

    verify(commonAncestor)
        .getCommonAncestor(syncSource2, FINALIZED_SLOT, chain.getChainHead().getSlot());

    source1CommonAncestor.complete(UInt64.valueOf(4243));
    assertThat(result).isNotDone();

    source2CommonAncestor.complete(UInt64.valueOf(4355));
    assertThat(result).isCompletedWithValue(FINALIZED_SLOT);
  }

  @Test
  void shouldUseLatestFinalizedSlotWhenOneSourceFailsToFindCommonAncestor() {
    final TargetChain chain =
        chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(10_000), dataStructureUtil.randomBytes32()),
            syncSource1,
            syncSource2);
    final SafeFuture<UInt64> source1CommonAncestor = new SafeFuture<>();
    final SafeFuture<UInt64> source2CommonAncestor = new SafeFuture<>();
    when(commonAncestor.getCommonAncestor(any(), any(), any()))
        .thenReturn(source1CommonAncestor)
        .thenReturn(source2CommonAncestor);

    final SafeFuture<UInt64> result = findCommonAncestor(chain);
    assertThat(result).isNotDone();

    verify(commonAncestor)
        .getCommonAncestor(syncSource1, FINALIZED_SLOT, chain.getChainHead().getSlot());

    verify(commonAncestor)
        .getCommonAncestor(syncSource2, FINALIZED_SLOT, chain.getChainHead().getSlot());

    source1CommonAncestor.completeExceptionally(new RuntimeException("Doh!"));
    source2CommonAncestor.complete(UInt64.valueOf(1485));
    assertThat(result).isCompletedWithValue(FINALIZED_SLOT);
  }

  private SafeFuture<UInt64> findCommonAncestor(final TargetChain chain) {
    eventThread.markAsOnEventThread();
    try {
      return commonAncestorFinder.findCommonAncestor(chain);
    } finally {
      eventThread.markAsOffEventThread();
    }
  }
}
