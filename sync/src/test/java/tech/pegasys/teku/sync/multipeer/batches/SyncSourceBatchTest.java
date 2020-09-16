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

package tech.pegasys.teku.sync.multipeer.batches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.teku.sync.multipeer.batches.BatchAssert.assertThatBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException.InvalidResponseType;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;

public class SyncSourceBatchTest extends AbstractBatchTest {
  private final InlineEventThread eventThread = new InlineEventThread();
  private final ConflictResolutionStrategy conflictResolutionStrategy =
      mock(ConflictResolutionStrategy.class);
  private final Map<Batch, List<StubSyncSource>> syncSources = new HashMap<>();

  @Test
  void requestMoreBlocks_shouldRequestFromStartOnFirstRequest() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(70, 50);
    batch.requestMoreBlocks(callback);
    verifyNoInteractions(callback);

    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(75));
    getSyncSource(batch).assertRequestedBlocks(70, 50);
    verify(callback).run();
    verifyNoMoreInteractions(callback);
  }

  @Test
  void requestMoreBlocks_shouldRequestFromSlotAfterLastBlockOnSubsequentRequests() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(70, 50);
    batch.requestMoreBlocks(callback);
    verifyNoInteractions(callback);

    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(75));
    getSyncSource(batch).assertRequestedBlocks(70, 50);
    verify(callback).run();
    verifyNoMoreInteractions(callback);

    batch.requestMoreBlocks(callback);
    getSyncSource(batch).assertRequestedBlocks(76, 44);
  }

  @Test
  void requestMoreBlocks_shouldResetAndSelectNewPeerAfterDisconnection() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(70, 50);
    batch.requestMoreBlocks(callback);

    // First request returns some data, so the batch isn't in initial state
    final StubSyncSource firstSyncSource = getSyncSource(batch);
    firstSyncSource.receiveBlocks(dataStructureUtil.randomSignedBeaconBlock(72));
    verify(callback).run();
    batch.markFirstBlockConfirmed();
    batch.markAsContested();

    // Second request should go to the same source
    batch.requestMoreBlocks(callback);
    firstSyncSource.assertRequestedBlocks(73, 47);

    assertThatBatch(batch).isNotEmpty();

    // But this requests fails
    firstSyncSource.failRequest(new PeerDisconnectedException());
    // The request is complete, so should call the callback
    verify(callback, times(2)).run();

    // And the batch should be back in initial state
    assertThatBatch(batch).isEmpty();
    assertThatBatch(batch).isNotContested();
    assertThatBatch(batch).hasUnconfirmedFirstBlock();

    // Third request selects a new peer to request data from
    batch.requestMoreBlocks(callback);
    assertThat(syncSources.get(batch)).hasSize(2);
    final StubSyncSource secondSyncSource = getSyncSource(batch);
    assertThat(secondSyncSource).isNotSameAs(firstSyncSource);
    secondSyncSource.assertRequestedBlocks(70, 50);
  }

  @Test
  void markContested_shouldVerifyBatchWithConflictResolutionStrategy() {
    final Batch batch = createBatch(1, 3);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch);
    batch.markAsContested();

    verify(conflictResolutionStrategy).verifyBatch(batch, getSyncSource(batch));
  }

  @Test
  void shouldBeInvalidWhenInconsistentResponseReceived() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(10, 10);
    batch.requestMoreBlocks(callback);

    requestError(
        batch,
        new BlocksByRangeResponseInvalidResponseException(
            InvalidResponseType.BLOCK_PARENT_ROOT_DOES_NOT_MATCH));

    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
    verify(callback).run();
    // Invalid blocks are discarded
    assertThatBatch(batch).isEmpty();
    assertThatBatch(batch).isNotComplete();
  }

  @Test
  void shouldReportAsInvalidToConflictResolutionStrategyWhenMarkedAsInvalid() {
    final Batch batch = createBatch(10, 10);
    batch.requestMoreBlocks(() -> {});
    batch.markAsInvalid();

    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
  }

  @Test
  void shouldNotReportAsInvalidToConflictResolutionStrategyWhenAlreadyContested() {
    final Batch batch = createBatch(10, 10);
    batch.requestMoreBlocks(() -> {});
    batch.markAsContested();

    verify(conflictResolutionStrategy).verifyBatch(batch, getSyncSource(batch));
    // Conflict resolution determines the batch is invalid
    batch.markAsInvalid();
    // But it shouldn't be notified again
    verifyNoMoreInteractions(conflictResolutionStrategy);
  }

  @Test
  void shouldReportAsInvalidWhenSecondRequestDoesNotFormChainWithExistingBlocks() {
    final Batch batch = createBatch(10, 10);

    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(10));
    verifyNoInteractions(conflictResolutionStrategy);

    // Second request returns a block whose parent doesn't match the previous block
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(11));

    // Node is disagreeing with itself so mark it as invalid
    verify(conflictResolutionStrategy).reportInvalidBatch(batch, getSyncSource(batch));
  }

  @Test
  void shouldSkipMakingRequestWhenNoTargetPeerIsAvailable() {
    final SyncSourceSelector emptySourceSelector = Optional::empty;
    final SyncSourceBatch batch =
        new SyncSourceBatch(
            eventThread,
            emptySourceSelector,
            conflictResolutionStrategy,
            targetChain,
            UInt64.ONE,
            UInt64.ONE);

    final Runnable callback = mock(Runnable.class);
    batch.requestMoreBlocks(callback);
    verify(callback).run();
    assertThatBatch(batch).isNotAwaitingBlocks();
  }

  @Override
  protected Batch createBatch(final long startSlot, final long count) {
    final List<StubSyncSource> syncSources = new ArrayList<>();
    final SyncSourceSelector syncSourceProvider =
        () -> {
          final StubSyncSource source = new StubSyncSource();
          syncSources.add(source);
          return Optional.of(source);
        };
    final SyncSourceBatch batch =
        new SyncSourceBatch(
            eventThread,
            syncSourceProvider,
            conflictResolutionStrategy,
            targetChain,
            UInt64.valueOf(startSlot),
            UInt64.valueOf(count));
    this.syncSources.put(batch, syncSources);
    return batch;
  }

  @Override
  protected void receiveBlocks(final Batch batch, final SignedBeaconBlock... blocks) {
    getSyncSource(batch).receiveBlocks(blocks);
  }

  @Override
  protected void requestError(final Batch batch, final Throwable error) {
    getSyncSource(batch).failRequest(error);
  }

  /** Get the most recent sync source for a batch. */
  private StubSyncSource getSyncSource(final Batch batch) {
    final List<StubSyncSource> syncSources = this.syncSources.get(batch);
    return syncSources.get(syncSources.size() - 1);
  }

  private static class StubSyncSource implements SyncSource {
    private final List<Pair<UInt64, UInt64>> requests = new ArrayList<>();
    private Optional<SafeFuture<Void>> currentRequest = Optional.empty();
    private Optional<ResponseStreamListener<SignedBeaconBlock>> currentListener = Optional.empty();

    public void receiveBlocks(final SignedBeaconBlock... blocks) {
      final ResponseStreamListener<SignedBeaconBlock> listener = currentListener.orElseThrow();
      Stream.of(blocks)
          .forEach(response -> assertThat(listener.onResponse(response)).isCompleted());
      currentRequest.orElseThrow().complete(null);
    }

    public void failRequest(final Throwable error) {
      currentRequest.orElseThrow().completeExceptionally(error);
    }

    @Override
    public SafeFuture<Void> requestBlocksByRange(
        final UInt64 startSlot,
        final UInt64 count,
        final UInt64 step,
        final ResponseStreamListener<SignedBeaconBlock> listener) {
      requests.add(Pair.of(startSlot, count));
      final SafeFuture<Void> request = new SafeFuture<>();
      currentRequest = Optional.of(request);
      currentListener = Optional.of(listener);
      return request;
    }

    @Override
    public SafeFuture<?> disconnectCleanly(final DisconnectReason reason) {
      return SafeFuture.COMPLETE;
    }

    public void assertRequestedBlocks(final long startSlot, final long count) {
      assertThat(requests).contains(Pair.of(UInt64.valueOf(startSlot), UInt64.valueOf(count)));
    }
  }
}
