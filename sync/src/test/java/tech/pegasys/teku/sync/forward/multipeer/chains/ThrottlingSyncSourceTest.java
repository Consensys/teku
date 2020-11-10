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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;

class ThrottlingSyncSourceTest {

  private static final int MAX_BLOCKS_PER_MINUTE = 100;
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final SyncSource delegate = mock(SyncSource.class);

  @SuppressWarnings("unchecked")
  private final ResponseStreamListener<SignedBeaconBlock> listener =
      mock(ResponseStreamListener.class);

  private final ThrottlingSyncSource source =
      new ThrottlingSyncSource(asyncRunner, timeProvider, delegate, MAX_BLOCKS_PER_MINUTE);

  @Test
  void shouldDelegateDisconnectImmediately() {
    final SafeFuture<Void> result = new SafeFuture<>();
    when(delegate.disconnectCleanly(DisconnectReason.REMOTE_FAULT)).thenReturn(result);

    final SafeFuture<Void> actual = source.disconnectCleanly(DisconnectReason.REMOTE_FAULT);

    ignoreFuture(verify(delegate).disconnectCleanly(DisconnectReason.REMOTE_FAULT));

    assertThat(actual).isSameAs(result);
  }

  @Test
  void shouldRequestBlocksImmediatelyIfRateLimitNotExceeded() {
    final UInt64 count = UInt64.valueOf(MAX_BLOCKS_PER_MINUTE - 1);
    ignoreFuture(source.requestBlocksByRange(UInt64.ZERO, count, ONE, listener));
    ignoreFuture(source.requestBlocksByRange(UInt64.valueOf(100), count, ONE, listener));

    // Both requests happen immediately
    ignoreFuture(verify(delegate).requestBlocksByRange(UInt64.ZERO, count, ONE, listener));
    ignoreFuture(verify(delegate).requestBlocksByRange(UInt64.valueOf(100), count, ONE, listener));
  }

  @Test
  void shouldDelayRequestIfBlockLimitAlreadyExceeded() {
    final UInt64 count = UInt64.valueOf(MAX_BLOCKS_PER_MINUTE);
    ignoreFuture(source.requestBlocksByRange(UInt64.ZERO, count, ONE, listener));
    ignoreFuture(source.requestBlocksByRange(UInt64.valueOf(100), count, ONE, listener));

    // Both requests happen immediately
    ignoreFuture(verify(delegate).requestBlocksByRange(UInt64.ZERO, count, ONE, listener));
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(61);
    asyncRunner.executeQueuedActions();

    ignoreFuture(verify(delegate).requestBlocksByRange(UInt64.valueOf(100), count, ONE, listener));
  }

  @Test
  void shouldContinueDelayingIfRequestStillExceeded() {
    final UInt64 count = UInt64.valueOf(MAX_BLOCKS_PER_MINUTE);
    ignoreFuture(source.requestBlocksByRange(UInt64.ZERO, count, ONE, listener));
    ignoreFuture(source.requestBlocksByRange(UInt64.valueOf(100), count, ONE, listener));

    // Both requests happen immediately
    ignoreFuture(verify(delegate).requestBlocksByRange(UInt64.ZERO, count, ONE, listener));
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(30);
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(delegate);

    timeProvider.advanceTimeBySeconds(31);
    asyncRunner.executeQueuedActions();
    ignoreFuture(verify(delegate).requestBlocksByRange(UInt64.valueOf(100), count, ONE, listener));
  }
}
