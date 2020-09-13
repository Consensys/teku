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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;

public class SyncSourceBatchTest extends AbstractBatchTest {
  private final InlineEventThread eventThread = new InlineEventThread();
  private final Map<Batch, StubSyncSource> syncSources = new HashMap<>();

  @Test
  void requestMoreBlocks_shouldRequestFromStartOnFirstRequest() {
    final Runnable callback = mock(Runnable.class);
    final Batch batch = createBatch(70, 50);
    batch.requestMoreBlocks(callback);
    verifyNoInteractions(callback);

    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(75));
    syncSources.get(batch).assertRequestedBlocks(70, 50);
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
    syncSources.get(batch).assertRequestedBlocks(70, 50);
    verify(callback).run();
    verifyNoMoreInteractions(callback);

    batch.requestMoreBlocks(callback);
    syncSources.get(batch).assertRequestedBlocks(76, 44);
  }

  @Override
  protected Batch createBatch(final long startSlot, final long count) {
    final StubSyncSource syncSource = new StubSyncSource();
    final SyncSourceBatch batch =
        new SyncSourceBatch(
            eventThread, syncSource, targetChain, UInt64.valueOf(startSlot), UInt64.valueOf(count));
    syncSources.put(batch, syncSource);
    return batch;
  }

  @Override
  protected void receiveBlocks(final Batch batch, final SignedBeaconBlock... blocks) {
    syncSources.get(batch).receiveBlocks(blocks);
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

    public void assertRequestedBlocks(final long startSlot, final long count) {
      assertThat(requests).contains(Pair.of(UInt64.valueOf(startSlot), UInt64.valueOf(count)));
    }
  }
}
