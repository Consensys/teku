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

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.sync.multipeer.batches.Batch;
import tech.pegasys.teku.sync.multipeer.batches.BatchChain;
import tech.pegasys.teku.sync.multipeer.batches.BatchFactory;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;

public class BatchDataRequester {
  private final EventThread eventThread;
  private final BatchChain activeBatches;
  private final BatchFactory batchFactory;
  private final UInt64 batchSize;
  private final int maxPendingBatches;

  public BatchDataRequester(
      final EventThread eventThread,
      final BatchChain activeBatches,
      final BatchFactory batchFactory,
      final UInt64 batchSize,
      final int maxPendingBatches) {
    this.eventThread = eventThread;
    this.activeBatches = activeBatches;
    this.batchFactory = batchFactory;
    this.batchSize = batchSize;
    this.maxPendingBatches = maxPendingBatches;
  }

  public void fillRetrievingQueue(
      final TargetChain targetChain,
      final UInt64 commonAncestorSlot,
      final Consumer<Batch> requestCompleteCallback) {
    eventThread.checkOnEventThread();

    final long pendingBatchesCount =
        activeBatches.stream().filter(batch -> !batch.isEmpty() || !batch.isComplete()).count();

    // First check if there are batches that should request more blocks
    activeBatches.stream()
        .filter(batch -> (!batch.isComplete() || batch.isContested()) && !batch.isAwaitingBlocks())
        .forEach(batch -> requestMoreBlocks(batch, requestCompleteCallback));

    // Add more pending batches if there is room
    UInt64 nextBatchStart = getNextSlotToRequest(commonAncestorSlot);
    final UInt64 targetSlot = targetChain.getChainHead().getSlot();
    for (long i = pendingBatchesCount;
        i < maxPendingBatches && nextBatchStart.isLessThan(targetSlot);
        i++) {
      final UInt64 remainingSlots = targetSlot.minus(nextBatchStart).plus(1);
      final UInt64 count = remainingSlots.min(batchSize);
      final Batch batch = batchFactory.createBatch(targetChain, nextBatchStart, count);
      activeBatches.add(batch);
      requestMoreBlocks(batch, requestCompleteCallback);
      nextBatchStart = batch.getLastSlot().plus(1);
      if (nextBatchStart.isGreaterThan(targetSlot)) {
        break;
      }
    }
  }

  private UInt64 getNextSlotToRequest(final UInt64 commonAncestorSlot) {
    final UInt64 lastRequestedSlot =
        activeBatches.last().map(Batch::getLastSlot).orElse(commonAncestorSlot);
    return lastRequestedSlot.plus(1);
  }

  private void requestMoreBlocks(final Batch batch, final Consumer<Batch> requestCompleteCallback) {
    batch.requestMoreBlocks(() -> eventThread.execute(() -> requestCompleteCallback.accept(batch)));
  }
}
