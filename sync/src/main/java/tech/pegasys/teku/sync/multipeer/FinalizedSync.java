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

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.NavigableSet;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.multipeer.BatchImporter.BatchImportResult;
import tech.pegasys.teku.sync.multipeer.batches.Batch;
import tech.pegasys.teku.sync.multipeer.batches.BatchChain;
import tech.pegasys.teku.sync.multipeer.batches.BatchFactory;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;

public class FinalizedSync implements Sync {
  private static final Logger LOG = LogManager.getLogger();

  private final EventThread eventThread;
  private final RecentChainData recentChainData;
  private final BatchImporter batchImporter;
  private final BatchDataRequester batchDataRequester;

  private final BatchChain activeBatches;

  private Optional<Batch> importingBatch = Optional.empty();

  private UInt64 commonAncestorSlot;

  private TargetChain targetChain;
  private SafeFuture<SyncResult> syncResult = SafeFuture.completedFuture(SyncResult.COMPLETE);

  private FinalizedSync(
      final EventThread eventThread,
      final RecentChainData recentChainData,
      final BatchChain activeBatches,
      final BatchImporter batchImporter,
      final BatchDataRequester batchDataRequester) {
    this.eventThread = eventThread;
    this.recentChainData = recentChainData;
    this.activeBatches = activeBatches;
    this.batchImporter = batchImporter;
    this.batchDataRequester = batchDataRequester;
  }

  public static FinalizedSync create(
      final EventThread eventThread,
      final RecentChainData recentChainData,
      final BatchImporter batchImporter,
      final BatchFactory batchFactory,
      final UInt64 batchSize) {
    final BatchChain activeBatches = new BatchChain();
    final BatchDataRequester batchDataRequester =
        new BatchDataRequester(eventThread, activeBatches, batchFactory, batchSize);
    return new FinalizedSync(
        eventThread, recentChainData, activeBatches, batchImporter, batchDataRequester);
  }

  /**
   * Begin a sync to the specified target chain. If a sync was previously in progress to a different
   * chain, the sync will switch to this new chain.
   *
   * @param targetChain the finalized chain that is the target to sync to
   * @return a future which completes when the sync finishes
   */
  @Override
  public SafeFuture<SyncResult> syncToChain(final TargetChain targetChain) {
    final SafeFuture<SyncResult> result = new SafeFuture<>();
    eventThread.execute(() -> switchSyncTarget(targetChain, result));
    return result;
  }

  private void switchSyncTarget(
      final TargetChain targetChain, final SafeFuture<SyncResult> syncResult) {
    LOG.debug("Switching to sync target {}", targetChain.getChainHead());
    eventThread.checkOnEventThread();
    // Cancel the existing sync
    this.syncResult.complete(SyncResult.TARGET_CHANGED);
    this.targetChain = targetChain;
    this.commonAncestorSlot = getCommonAncestorSlot();
    this.syncResult = syncResult;
    fillRetrievingQueue();
  }

  private UInt64 getCommonAncestorSlot() {
    return compute_start_slot_at_epoch(recentChainData.getFinalizedEpoch());
  }

  private void onBatchReceivedBlocks(final Batch batch) {
    eventThread.checkOnEventThread();
    if (!isActiveBatch(batch)) {
      LOG.debug("Ignoring update from batch {} as it is no longer useful", batch);
      return;
    }

    activeBatches
        .previousNonEmptyBatch(batch)
        .ifPresentOrElse(
            previousBatch -> checkBatchesFormChain(previousBatch, batch),
            () -> {
              // There are no previous blocks awaiting import, check if the batch builds on our
              // chain
              batch
                  .getFirstBlock()
                  .ifPresent(firstBlock -> checkBatchMatchesStartingPoint(batch, firstBlock));
            });

    activeBatches
        .nextNonEmptyBatch(batch)
        .ifPresentOrElse(
            followingBatch -> checkBatchesFormChain(batch, followingBatch),
            () -> checkAgainstTargetHead(batch));

    startNextImport();
    fillRetrievingQueue();
  }

  private void checkAgainstTargetHead(final Batch batch) {
    if (batchEndsChain(batch)) {
      batch.markComplete();
      batch.markLastBlockConfirmed();
    } else if (batch.isComplete()
        && batch.getLastSlot().equals(targetChain.getChainHead().getSlot())) {
      // We reached the target slot, but not the root that was claimed
      if (batch.isEmpty()) {
        // We didn't get any blocks - maybe we should have, or maybe a previous batch was wrong
        // Contest all batches back to the last non-empty batch
        final NavigableSet<Batch> batchesFromLastBlock =
            activeBatches
                .previousNonEmptyBatch(batch)
                .map(activeBatches::batchesAfterInclusive)
                .orElseGet(() -> activeBatches.batchesBeforeInclusive(batch));
        // If any are incomplete, we might yet get the blocks we need
        // Otherwise at least one of them is wrong so contest them all
        if (batchesFromLastBlock.stream().allMatch(Batch::isComplete)) {
          LOG.debug(
              "Contesting {} batches because blocks do not lead to target chain head",
              batchesFromLastBlock.size());
          markBatchesAsContested(batchesFromLastBlock);
        }
      } else {
        // We got blocks but they didn't lead to the target so must be invalid
        LOG.debug(
            "Marking batch {} as invalid because returned blocks do not match the sync target",
            batch);
        batch.markAsInvalid();
      }
    }
  }

  private Boolean batchEndsChain(final Batch batch) {
    return batch
        .getLastBlock()
        .map(lastBlock -> lastBlock.getRoot().equals(targetChain.getChainHead().getBlockRoot()))
        .orElse(false);
  }

  private void checkBatchMatchesStartingPoint(
      final Batch batch, final SignedBeaconBlock firstBlock) {
    final NavigableSet<Batch> previousBatches = activeBatches.batchesBeforeExclusive(batch);
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
      final NavigableSet<Batch> contestedBatches = activeBatches.batchesBeforeInclusive(batch);
      LOG.debug(
          "Contesting {} batches because first block does not match sync start point",
          contestedBatches.size());
      markBatchesAsContested(contestedBatches);
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
          firstBatch, secondBatch, activeBatches.batchesBetweenExclusive(firstBatch, secondBatch));
      return;
    }
    if (!firstBatch.isComplete() || secondBatch.isEmpty()) {
      return;
    }
    if (!firstBatch.getTargetChain().equals(secondBatch.getTargetChain())) {
      // We switched chains but they didn't actually match up. Go back to the finalized epoch
      activeBatches.removeAll();
      commonAncestorSlot = getCommonAncestorSlot();
      return;
    }

    final boolean allComplete =
        activeBatches.batchesBetweenExclusive(firstBatch, secondBatch).stream()
            .allMatch(Batch::isComplete);
    if (!allComplete) {
      // Not a conflict, because we're still waiting on some blocks in the middle
      return;
    }
    final NavigableSet<Batch> contestedBatches =
        activeBatches.batchesBetweenInclusive(firstBatch, secondBatch);
    LOG.debug(
        "Marking {} batches as contested because {} and {} do not form a chain",
        contestedBatches.size(),
        firstBatch,
        secondBatch);
    markBatchesAsContested(contestedBatches);
    // Otherwise there must be a block in firstBatch we haven't received yet
  }

  private void startNextImport() {
    if (importingBatch.isPresent()) {
      return;
    }
    activeBatches
        .firstImportableBatch()
        .ifPresent(
            batch -> {
              importingBatch = Optional.of(batch);
              batchImporter
                  .importBatch(batch)
                  .thenAcceptAsync(result -> onImportComplete(result, batch), eventThread)
                  .reportExceptions();
            });
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
    contestedBatches.forEach(Batch::markAsContested);
  }

  private void onImportComplete(final BatchImportResult result, final Batch importedBatch) {
    eventThread.checkOnEventThread();
    if (!isActiveBatch(importedBatch)) {
      return;
    }
    checkState(
        importingBatch.isPresent() && importingBatch.get().equals(importedBatch),
        "Received import complete for batch that shouldn't have been importing");
    importingBatch = Optional.empty();
    if (result.isFailure()) {
      // Mark all batches that form a chain with this one as invalid
      for (Batch batch : activeBatches.batchesAfterInclusive(importedBatch)) {
        if (!batch.isFirstBlockConfirmed()) {
          break;
        }
        LOG.debug("Marking batch {} as invalid because it extends from an invalid block", batch);
        batch.markAsInvalid();
      }
    } else {
      // Everything prior to this batch must already exist on our chain so we can drop them all
      activeBatches.removeUpToIncluding(importedBatch);
      commonAncestorSlot = importedBatch.getLastSlot();
    }
    startNextImport();
    fillRetrievingQueue();
    if (activeBatches.isEmpty()) {
      syncResult.complete(SyncResult.COMPLETE);
    }
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
    batchDataRequester.fillRetrievingQueue(
        targetChain, commonAncestorSlot, this::onBatchReceivedBlocks);
  }

  @VisibleForTesting
  boolean isActiveBatch(final Batch batch) {
    return activeBatches.contains(batch);
  }
}
