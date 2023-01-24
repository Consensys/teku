/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beacon.sync.forward.multipeer.batches;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.statetransition.blobs.BlobsSidecarManager;

public class SyncSourceBatch implements Batch {
  private static final Logger LOG = LogManager.getLogger();

  private final EventThread eventThread;
  private final SyncSourceSelector syncSourceProvider;
  private final ConflictResolutionStrategy conflictResolutionStrategy;
  private final TargetChain targetChain;
  private final BlobsSidecarManager blobsSidecarManager;
  private final UInt64 firstSlot;
  private final UInt64 count;

  private Optional<SyncSource> currentSyncSource = Optional.empty();
  private boolean complete = false;
  private boolean contested = false;
  private boolean firstBlockConfirmed = false;
  private boolean lastBlockConfirmed = false;
  private boolean awaitingBlocks = false;

  private final List<SignedBeaconBlock> blocks = new ArrayList<>();
  private final Map<UInt64, BlobsSidecar> blobsSidecarsBySlot = new HashMap<>();

  SyncSourceBatch(
      final EventThread eventThread,
      final SyncSourceSelector syncSourceProvider,
      final ConflictResolutionStrategy conflictResolutionStrategy,
      final TargetChain targetChain,
      final BlobsSidecarManager blobsSidecarManager,
      final UInt64 firstSlot,
      final UInt64 count) {
    checkArgument(
        count.isGreaterThanOrEqualTo(UInt64.ONE), "Must include at least one slot in a batch");
    this.eventThread = eventThread;
    this.syncSourceProvider = syncSourceProvider;
    this.conflictResolutionStrategy = conflictResolutionStrategy;
    this.targetChain = targetChain;
    this.blobsSidecarManager = blobsSidecarManager;
    this.firstSlot = firstSlot;
    this.count = count;
  }

  @Override
  public UInt64 getFirstSlot() {
    return firstSlot;
  }

  @Override
  public UInt64 getLastSlot() {
    return firstSlot.plus(count).minus(1);
  }

  @Override
  public UInt64 getCount() {
    return count;
  }

  @Override
  public Optional<SignedBeaconBlock> getFirstBlock() {
    return blocks.isEmpty() ? Optional.empty() : Optional.of(blocks.get(0));
  }

  @Override
  public Optional<SignedBeaconBlock> getLastBlock() {
    return blocks.isEmpty() ? Optional.empty() : Optional.of(blocks.get(blocks.size() - 1));
  }

  @Override
  public List<SignedBeaconBlock> getBlocks() {
    return blocks;
  }

  @Override
  public Map<UInt64, BlobsSidecar> getBlobsSidecarsBySlot() {
    return blobsSidecarsBySlot;
  }

  @Override
  public Optional<SyncSource> getSource() {
    return currentSyncSource;
  }

  @Override
  public void markComplete() {
    complete = true;
  }

  @Override
  public boolean isComplete() {
    return complete;
  }

  @Override
  public boolean isConfirmed() {
    return firstBlockConfirmed && lastBlockConfirmed;
  }

  @Override
  public boolean isFirstBlockConfirmed() {
    return firstBlockConfirmed;
  }

  @Override
  public boolean isContested() {
    return contested;
  }

  @Override
  public boolean isEmpty() {
    return blocks.isEmpty();
  }

  @Override
  public boolean isAwaitingBlocks() {
    return awaitingBlocks;
  }

  @Override
  public void markFirstBlockConfirmed() {
    final boolean wasConfirmed = isConfirmed();
    firstBlockConfirmed = true;
    checkIfNewlyConfirmed(wasConfirmed);
  }

  @Override
  public void markLastBlockConfirmed() {
    final boolean wasConfirmed = isConfirmed();
    lastBlockConfirmed = true;
    checkIfNewlyConfirmed(wasConfirmed);
  }

  private void checkIfNewlyConfirmed(final boolean wasConfirmed) {
    if (!wasConfirmed && isConfirmed()) {
      currentSyncSource.ifPresent(
          source -> conflictResolutionStrategy.reportConfirmedBatch(this, source));
    }
  }

  @Override
  public void markAsContested() {
    contested = true;
    currentSyncSource.ifPresentOrElse(
        source -> conflictResolutionStrategy.verifyBatch(this, source), this::reset);
  }

  @Override
  public void markAsInvalid() {
    if (!contested) {
      currentSyncSource.ifPresent(
          source -> conflictResolutionStrategy.reportInvalidBatch(this, source));
    }
    currentSyncSource = Optional.empty();
    reset();
  }

  @Override
  public void requestMoreBlocks(final Runnable callback) {
    checkState(
        !isComplete() || isContested(), "Attempting to request more blocks from a complete batch");
    final BlockRequestHandler blockRequestHandler = new BlockRequestHandler();
    final UInt64 startSlot =
        getLastBlock().map(SignedBeaconBlock::getSlot).map(UInt64::increment).orElse(firstSlot);
    final UInt64 remainingSlots = count.minus(startSlot.minus(firstSlot));
    final UInt64 lastSlot = startSlot.plus(remainingSlots).decrement();

    checkState(
        remainingSlots.isGreaterThan(UInt64.ZERO),
        "Attempting to request more blocks when block for last slot already present.");
    if (currentSyncSource.isEmpty()) {
      currentSyncSource = syncSourceProvider.selectSource();
      if (currentSyncSource.isEmpty()) {
        eventThread.executeLater(callback);
        return;
      }
    }
    awaitingBlocks = true;
    final SyncSource syncSource = currentSyncSource.orElseThrow();

    LOG.debug(
        "Requesting blocks for {} slots starting at {} from peer {}",
        remainingSlots,
        startSlot,
        syncSource);

    final SafeFuture<Void> blocksRequest =
        syncSource.requestBlocksByRange(startSlot, remainingSlots, blockRequestHandler);

    final SafeFuture<Void> blobsSidecarsRequest;
    if (blobsSidecarManager.isStorageOfBlobsSidecarRequired(lastSlot)) {
      final UInt64 blobsSidecarStartSlot = getFirstSlotInEip4844(startSlot, remainingSlots);
      final UInt64 blobsSidecarCount = lastSlot.minusMinZero(blobsSidecarStartSlot).plus(1);
      LOG.debug(
          "Requesting blobs sidecars for {} slots starting at {} from peer {}",
          blobsSidecarCount,
          blobsSidecarStartSlot,
          syncSource);
      final BlobsSidecarRequestHandler blobsSidecarRequestHandler =
          new BlobsSidecarRequestHandler();
      blobsSidecarsRequest =
          syncSource.requestBlobsSidecarsByRange(
              blobsSidecarStartSlot, blobsSidecarCount, blobsSidecarRequestHandler);
    } else {
      blobsSidecarsRequest = SafeFuture.COMPLETE;
    }

    SafeFuture.allOfFailFast(blocksRequest, blobsSidecarsRequest)
        .thenRunAsync(() -> onRequestComplete(blockRequestHandler), eventThread)
        .handleAsync(
            (__, error) -> {
              if (error != null) {
                handleRequestErrors(error);
              }
              // Ensure there is time for other events to be processed before the callback completes
              // Allows external events like peers disconnecting to be processed before retrying
              eventThread.executeLater(callback);
              return null;
            },
            eventThread)
        .ifExceptionGetsHereRaiseABug();
  }

  // TODO: refactor very ugly
  private UInt64 getFirstSlotInEip4844(final UInt64 startSlot, final UInt64 count) {
    return UInt64.range(startSlot, startSlot.plus(count))
        .filter(blobsSidecarManager::isStorageOfBlobsSidecarRequired)
        .findFirst()
        .orElseThrow();
  }

  private void handleRequestErrors(final Throwable error) {
    eventThread.checkOnEventThread();
    awaitingBlocks = false;
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof PeerDisconnectedException) {
      LOG.debug(
          "Failed to retrieve blocks from {} to {}, because peer disconnected",
          getFirstSlot(),
          getLastSlot(),
          error);
      markAsInvalid();
    } else if (rootCause instanceof BlocksByRangeResponseInvalidResponseException) {
      LOG.debug("Inconsistent blocks returned from blocks by range request", error);
      markAsInvalid();
    } else {
      LOG.debug("Error while requesting blocks", error);
      currentSyncSource = Optional.empty();
      reset();
    }
  }

  private void reset() {
    complete = false;
    contested = false;
    firstBlockConfirmed = false;
    lastBlockConfirmed = false;
    blocks.clear();
    blobsSidecarsBySlot.clear();
  }

  private void onRequestComplete(final BlockRequestHandler blockRequestHandler) {
    eventThread.checkOnEventThread();
    final List<SignedBeaconBlock> newBlocks = blockRequestHandler.complete();

    awaitingBlocks = false;
    if (!blocks.isEmpty() && !newBlocks.isEmpty()) {
      final SignedBeaconBlock previousBlock = blocks.get(blocks.size() - 1);
      final SignedBeaconBlock firstNewBlock = newBlocks.get(0);
      if (!firstNewBlock.getParentRoot().equals(previousBlock.getRoot())) {
        LOG.debug(
            "Marking batch invalid because new blocks do not form a chain with previous blocks");
        markAsInvalid();
        return;
      }
    }
    blocks.addAll(newBlocks);
    if (newBlocks.isEmpty()
        || newBlocks.get(newBlocks.size() - 1).getSlot().equals(getLastSlot())) {
      complete = true;
    }
  }

  @Override
  public TargetChain getTargetChain() {
    return targetChain;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("firstSlot", firstSlot)
        .add("lastSlot", getLastSlot())
        .add("count", count)
        .add("complete", complete)
        .add(
            "requiredParent",
            getFirstBlock()
                .map(block -> LogFormatter.formatHashRoot(block.getParentRoot()))
                .orElse("<unknown>"))
        .add("firstBlock", getFirstBlock().map(this::formatBlockAndParent).orElse("<none>"))
        .add(
            "lastBlock",
            getLastBlock().map(MinimalBeaconBlockSummary::toLogString).orElse("<none>"))
        .toString();
  }

  private String formatBlockAndParent(final SignedBeaconBlock block1) {
    return block1.toLogString()
        + " (parent: "
        + LogFormatter.formatHashRoot(block1.getParentRoot())
        + ")";
  }

  private static class BlockRequestHandler implements RpcResponseListener<SignedBeaconBlock> {
    private final List<SignedBeaconBlock> blocks = new ArrayList<>();

    @Override
    public SafeFuture<?> onResponse(final SignedBeaconBlock block) {
      blocks.add(block);
      return SafeFuture.COMPLETE;
    }

    public List<SignedBeaconBlock> complete() {
      return blocks;
    }
  }

  private class BlobsSidecarRequestHandler implements RpcResponseListener<BlobsSidecar> {

    @Override
    public SafeFuture<?> onResponse(final BlobsSidecar response) {
      blobsSidecarsBySlot.put(response.getBeaconBlockSlot(), response);
      return SafeFuture.COMPLETE;
    }
  }
}
