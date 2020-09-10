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

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.multipeer.BatchImporter.BatchImportResult;
import tech.pegasys.teku.sync.multipeer.batches.Batch;
import tech.pegasys.teku.sync.multipeer.batches.BatchFactory;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;

public class FinalizedSync {
  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_PENDING_BATCHES = 5;

  private final EventThread eventThread;
  private final RecentChainData recentChainData;
  private final BatchImporter batchImporter;
  private final BatchFactory batchFactory;
  private final UInt64 batchSize;

  private final NavigableSet<Batch> activeBatches =
      new TreeSet<>(Comparator.comparing(Batch::getFirstSlot));

  private UInt64 commonAncestorSlot;

  private TargetChain targetChain;

  public FinalizedSync(
      final EventThread eventThread,
      final RecentChainData recentChainData,
      final BatchImporter batchImporter,
      final BatchFactory batchFactory,
      final UInt64 batchSize) {
    this.eventThread = eventThread;
    this.recentChainData = recentChainData;
    this.batchImporter = batchImporter;
    this.batchFactory = batchFactory;
    this.batchSize = batchSize;
  }

  /**
   * Begin a sync to the specified target chain. If a sync was previously in progress to a different
   * chain, the sync will switch to this new chain.
   *
   * @param targetChain the finalized chain that is the target to sync to
   */
  void syncToChain(final TargetChain targetChain) {
    eventThread.execute(() -> switchSyncTarget(targetChain));
  }

  private void switchSyncTarget(final TargetChain targetChain) {
    eventThread.checkOnEventThread();
    this.targetChain = targetChain;
    this.commonAncestorSlot = compute_start_slot_at_epoch(recentChainData.getFinalizedEpoch());
    fillRetrievingQueue();
  }

  private void onBatchReceivedBlocks(final Batch batch) {
    eventThread.checkOnEventThread();
    if (!isActiveBatch(batch)) {
      LOG.debug("Ignoring update from batch {} as it is no longer useful", batch);
      return;
    }

    final NavigableSet<Batch> previousBatches = activeBatches.headSet(batch, false);
    final Optional<Batch> firstNonEmptyPreviousBatch =
        previousBatches.descendingSet().stream()
            .filter(previousBatch -> !previousBatch.isEmpty())
            .findFirst();
    firstNonEmptyPreviousBatch.ifPresentOrElse(
        previousBatch -> checkBatchesFormChain(previousBatch, batch),
        () -> {
          // There are no previous blocks awaiting import, check if the batch builds on our chain
          batch
              .getFirstBlock()
              .ifPresent(
                  firstBlock -> checkBatchMatchesStartingPoint(batch, previousBatches, firstBlock));
        });

    final Optional<Batch> firstNonEmptyFollowingBatch =
        activeBatches.tailSet(batch, false).stream()
            .filter(followingBatch -> !followingBatch.isEmpty())
            .findFirst();
    firstNonEmptyFollowingBatch.ifPresent(
        followingBatch -> checkBatchesFormChain(batch, followingBatch));

    startingPendingImports();
    fillRetrievingQueue();
  }

  private void checkBatchMatchesStartingPoint(
      final Batch batch,
      final NavigableSet<Batch> previousBatches,
      final SignedBeaconBlock firstBlock) {
    if (isChildOfStartingPoint(firstBlock)) {
      // We found where this chain connects to ours so all prior empty batches are
      // complete and our first block is valid.
      batch.markFirstBlockConfirmed();
      previousBatches.forEach(this::confirmEmptyBatch);
    } else if (previousBatches.isEmpty()) {
      // There are no previous batches but this doesn't match our chain so must be invalid
      batch.markAsInvalid();
    } else if (previousBatches.stream().allMatch(Batch::isComplete)) {
      // All the previous batches claim to be empty but we don't match up.
      markBatchesAsContested(activeBatches.headSet(batch, true));
    }
  }

  private boolean isChildOfStartingPoint(final SignedBeaconBlock firstBlock) {
    // TODO: We should track where we actually started requesting blocks from and check it matches
    // that block, not just any block we have.
    return recentChainData.containsBlock(firstBlock.getParent_root());
  }

  private void checkBatchesFormChain(final Batch firstBatch, final Batch secondBatch) {
    if (batchesFormChain(firstBatch, secondBatch)) {
      markBatchesAsFormingChain(
          firstBatch, secondBatch, activeBatches.subSet(firstBatch, false, secondBatch, false));
    } else if (firstBatch.isComplete()) {
      markBatchesAsContested(activeBatches.subSet(firstBatch, true, secondBatch, true));
    }
    // Otherwise there must be a block in firstBatch we haven't received yet
  }

  private void startingPendingImports() {
    // TODO: Should only have one batch importing at a time.
    // TODO: Should actually mark the batch as importing.
    for (final Batch batch : activeBatches) {
      if (batch.isImporting() || batch.isEmpty()) {
        continue;
      }
      if (!batch.isConfirmed()) {
        break;
      }
      importBatch(batch);
    }
  }

  private void markBatchesAsFormingChain(
      final Batch firstBatch,
      final Batch secondBatch,
      final Collection<Batch> confirmedEmptyBatches) {
    firstBatch.markComplete();
    firstBatch.markLastBlockConfirmed();
    secondBatch.markFirstBlockConfirmed();
    confirmedEmptyBatches.forEach(this::confirmEmptyBatch);
  }

  private void confirmEmptyBatch(final Batch emptyBatch) {
    emptyBatch.markComplete();
    emptyBatch.markFirstBlockConfirmed();
    emptyBatch.markLastBlockConfirmed();
  }

  private void markBatchesAsContested(final NavigableSet<Batch> contestedBatches) {
    // TODO: If any are already contested, mark them invalid and leave others?
    contestedBatches.forEach(Batch::markAsContested);
  }

  private void importBatch(final Batch batch) {
    batchImporter
        .importBatch(batch)
        .thenAcceptAsync(result -> onImportComplete(result, batch), eventThread)
        .reportExceptions();
  }

  private void onImportComplete(final BatchImportResult result, final Batch importedBatch) {
    eventThread.checkOnEventThread();
    if (!isActiveBatch(importedBatch)) {
      return;
    }
    if (result.isFailure()) {
      // Mark all batches that form a chain with this one as invalid
      for (Batch batch : activeBatches.tailSet(importedBatch, true)) {
        if (!batch.isFirstBlockConfirmed()) {
          break;
        }
        batch.markAsInvalid();
      }
    } else {
      // Everything prior to this batch must already exist on our chain so we can drop them all
      activeBatches.headSet(importedBatch, true).clear();
      commonAncestorSlot = importedBatch.getLastSlot();
    }
    fillRetrievingQueue();
  }

  private Boolean batchesFormChain(final Batch previousBatch, final Batch secondBatch) {
    if (previousBatch.getLastBlock().isEmpty() || secondBatch.getFirstBlock().isEmpty()) {
      return false;
    }
    final SignedBeaconBlock lastBlock = previousBatch.getLastBlock().get();
    final SignedBeaconBlock firstBlock = secondBatch.getFirstBlock().get();
    return blocksForChain(lastBlock, firstBlock);
  }

  private Boolean blocksForChain(
      final SignedBeaconBlock lastBlock, final SignedBeaconBlock firstBlock) {
    return lastBlock.getRoot().equals(firstBlock.getParent_root());
  }

  private void fillRetrievingQueue() {
    // TODO: Check if we have reached the chain target
    eventThread.checkOnEventThread();

    final long incompleteBatchCount =
        activeBatches.stream().filter(batch -> !batch.isComplete()).count();

    // First check if there are batches that should request more blocks
    activeBatches.stream()
        .filter(batch -> !batch.isComplete() && !batch.isAwaitingBlocks())
        .forEach(this::requestMoreBlocks);

    // Add more pending batches if there is room
    UInt64 nextBatchStart = getNextSlotToRequest();
    for (long i = incompleteBatchCount; i < MAX_PENDING_BATCHES; i++) {
      final Batch batch = batchFactory.createBatch(targetChain, nextBatchStart, batchSize);
      activeBatches.add(batch);
      requestMoreBlocks(batch);
      nextBatchStart = nextBatchStart.plus(batchSize);
    }
  }

  private UInt64 getNextSlotToRequest() {
    final UInt64 lastRequestedSlot =
        activeBatches.isEmpty() ? this.commonAncestorSlot : activeBatches.last().getLastSlot();
    return lastRequestedSlot.plus(1);
  }

  private void requestMoreBlocks(final Batch batch) {
    batch.requestMoreBlocks(() -> eventThread.execute(() -> onBatchReceivedBlocks(batch)));
  }

  @VisibleForTesting
  boolean isActiveBatch(final Batch batch) {
    return activeBatches.contains(batch);
  }
}
