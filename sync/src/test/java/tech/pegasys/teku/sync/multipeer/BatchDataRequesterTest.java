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

package tech.pegasys.teku.sync.multipeer;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.sync.multipeer.batches.BatchAssert.assertThatBatch;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.sync.multipeer.batches.Batch;
import tech.pegasys.teku.sync.multipeer.batches.BatchChain;
import tech.pegasys.teku.sync.multipeer.batches.BatchFactory;
import tech.pegasys.teku.sync.multipeer.batches.StubBatch;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.multipeer.chains.TargetChainTestUtil;

class BatchDataRequesterTest {
  private static final UInt64 BATCH_SIZE = UInt64.valueOf(50);
  private static final int MAX_PENDING_BATCHES = 5;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final InlineEventThread eventThread = new InlineEventThread();
  private final BatchChain batchChain = new BatchChain();
  private final BatchFactory batchFactory = mock(BatchFactory.class);

  @SuppressWarnings("unchecked")
  private final Consumer<Batch> requestCompleteCallback = mock(Consumer.class);

  private final TargetChain targetChain =
      TargetChainTestUtil.chainWith(
          new SlotAndBlockRoot(UInt64.valueOf(500), dataStructureUtil.randomBytes32()));

  private final BatchDataRequester batchDataRequester =
      new BatchDataRequester(
          eventThread, batchChain, batchFactory, BATCH_SIZE, MAX_PENDING_BATCHES);

  @BeforeEach
  void setUp() {
    when(batchFactory.createBatch(eq(targetChain), any(), any()))
        .thenAnswer(
            invocation ->
                new StubBatch(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)));
  }

  @Test
  void shouldCreateNewBatchesWhenChainIsEmpty() {
    fillQueue(UInt64.valueOf(24));

    final List<Batch> batches = batchChain.stream().collect(toList());
    assertThat(batches).hasSize(MAX_PENDING_BATCHES);
    assertThatBatch(batches.get(0)).hasRange(25, 74);
    assertThatBatch(batches.get(1)).hasRange(75, 124);
    assertThatBatch(batches.get(2)).hasRange(125, 174);
    assertThatBatch(batches.get(3)).hasRange(175, 224);
    assertThatBatch(batches.get(4)).hasRange(225, 274);
    batches.forEach(batch -> assertThatBatch(batch).isAwaitingBlocks());
  }

  @Test
  void shouldRequestAdditionalDataFromBatchesThatAreNotYetComplete() {
    // Block with some blocks, but not yet complete
    final StubBatch batch = new StubBatch(targetChain, UInt64.valueOf(6), UInt64.valueOf(10));
    batch.requestMoreBlocks(() -> {});
    batch.receiveBlocks(dataStructureUtil.randomSignedBeaconBlock(8));

    batchChain.add(batch);

    fillQueue(ZERO);

    // Should request additional blocks for the incomplete batch
    assertThatBatch(batch).isAwaitingBlocks();

    // And only create new batches up to the limit
    assertThat(batchChain.stream()).hasSize(MAX_PENDING_BATCHES);
  }

  @Test
  void shouldNotIncludeEmptyBatchesInThePendingBatchCount() {
    final StubBatch batch = new StubBatch(targetChain, UInt64.valueOf(6), UInt64.valueOf(10));
    batch.markComplete(); // Complete empty batch
    batchChain.add(batch);

    fillQueue(ZERO);

    // Shouldn't request more blocks for a complete batch
    assertThatBatch(batch).isNotAwaitingBlocks();

    // Exclude the empty batch from the pending batch count, so we can have one more than otherwise
    assertThat(batchChain.stream()).hasSize(MAX_PENDING_BATCHES + 1);
  }

  @Test
  void shouldIncludeNonEmptyIncompleteBatchesInThePendingBatchCount() {
    // Add a non-empty, complete batch
    final StubBatch batch = new StubBatch(targetChain, UInt64.valueOf(6), UInt64.valueOf(10));
    batch.requestMoreBlocks(() -> {});
    batch.receiveBlocks(dataStructureUtil.randomSignedBeaconBlock(batch.getLastSlot()));
    batchChain.add(batch);

    fillQueue(ZERO);

    // Shouldn't request more blocks for a complete batch
    assertThatBatch(batch).isNotAwaitingBlocks();

    // Complete batch is including in the pending count as it has blocks to import
    assertThat(batchChain.stream()).hasSize(MAX_PENDING_BATCHES);
  }

  @Test
  void shouldNotScheduleMoreBatchesWhenTargetSlotReached() {
    final StubBatch batch = new StubBatch(targetChain, UInt64.valueOf(440), BATCH_SIZE);
    batchChain.add(batch);

    fillQueue(ZERO);

    final List<Batch> batches = batchChain.stream().collect(toList());
    assertThat(batches).hasSize(2);
    assertThatBatch(batches.get(0)).hasRange(440, 489);
    assertThatBatch(batches.get(1)).hasRange(490, targetChain.getChainHead().getSlot().longValue());
  }

  private void fillQueue(final UInt64 commonAncestorSlot) {
    eventThread.execute(
        () ->
            batchDataRequester.fillRetrievingQueue(
                targetChain, commonAncestorSlot, requestCompleteCallback));
  }
}
