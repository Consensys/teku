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

package tech.pegasys.teku.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;

public class CommonAncestorTest extends AbstractSyncTest {

  @Test
  void shouldNotSearchCommonAncestorWithoutSufficientLocalData()
      throws ExecutionException, InterruptedException {
    final UInt64 firstNonFinalSlot = dataStructureUtil.randomUInt64();
    final UInt64 currentLocalHead = firstNonFinalSlot.plus(9999);
    final CommonAncestor commonAncestor = new CommonAncestor(storageClient);
    final PeerStatus status =
        withPeerHeadSlot(
            currentLocalHead,
            compute_epoch_at_slot(currentLocalHead),
            dataStructureUtil.randomBytes32());
    when(storageClient.getHeadSlot()).thenReturn(currentLocalHead);

    assertThat(commonAncestor.getCommonAncestor(peer, status, firstNonFinalSlot).get())
        .isEqualTo(firstNonFinalSlot);
  }

  @Test
  void shouldNotSearchCommonAncestorWithoutSufficientRemoteData()
      throws ExecutionException, InterruptedException {
    final UInt64 firstNonFinalSlot = dataStructureUtil.randomUInt64();
    final UInt64 currentLocalHead = firstNonFinalSlot.plus(20000);
    final UInt64 currentRemoteHead = firstNonFinalSlot.plus(9999);
    final CommonAncestor commonAncestor = new CommonAncestor(storageClient);
    final PeerStatus status =
        withPeerHeadSlot(
            currentRemoteHead,
            compute_epoch_at_slot(currentRemoteHead),
            dataStructureUtil.randomBytes32());
    when(storageClient.getHeadSlot()).thenReturn(currentLocalHead);

    assertThat(commonAncestor.getCommonAncestor(peer, status, firstNonFinalSlot).get())
        .isEqualTo(firstNonFinalSlot);
  }

  @Test
  void shouldSearchCommonAncestorWithSufficientRemoteData()
      throws ExecutionException, InterruptedException {
    final UInt64 firstNonFinalSlot = dataStructureUtil.randomUInt64();
    final UInt64 currentLocalHead = firstNonFinalSlot.plus(100000);
    final UInt64 currentRemoteHead = firstNonFinalSlot.plus(100001);
    final CommonAncestor commonAncestor = new CommonAncestor(storageClient);
    final SafeFuture<Void> requestFuture1 = new SafeFuture<>();
    when(peer.requestBlocksByRange(any(), eq(UInt64.valueOf(20)), eq(UInt64.valueOf(50)), any()))
        .thenReturn(requestFuture1);
    final PeerStatus status =
        withPeerHeadSlot(
            currentRemoteHead,
            compute_epoch_at_slot(currentRemoteHead),
            dataStructureUtil.randomBytes32());
    when(storageClient.getHeadSlot()).thenReturn(currentLocalHead);
    when(storageClient.containsBlock(any())).thenReturn(true);

    SafeFuture<UInt64> futureSlot =
        commonAncestor.getCommonAncestor(peer, status, firstNonFinalSlot);

    verify(peer)
        .requestBlocksByRange(
            eq(currentLocalHead.minus(3000)),
            eq(UInt64.valueOf(20)),
            eq(UInt64.valueOf(50)),
            responseListenerArgumentCaptor.capture());

    assertThat(futureSlot.isDone()).isFalse();
    final ResponseStreamListener<SignedBeaconBlock> responseListener =
        responseListenerArgumentCaptor.getValue();
    respondWithBlocksAtSlots(
        responseListener, currentLocalHead.minus(2850), currentLocalHead.minus(2250));
    requestFuture1.complete(null);
    assertThat(futureSlot.get()).isEqualTo(currentLocalHead.minus(2250));
  }
}
