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
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRangeResponseInvalidResponseException;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;

public class SyncSourceBatch implements Batch {
  private static final Logger LOG = LogManager.getLogger();

  private final EventThread eventThread;
  private final BlobSidecarManager blobSidecarManager;
  private final SyncSourceSelector syncSourceProvider;
  private final ConflictResolutionStrategy conflictResolutionStrategy;
  private final TargetChain targetChain;
  private final UInt64 firstSlot;
  private final UInt64 count;

  private Optional<SyncSource> currentSyncSource = Optional.empty();
  private boolean complete = false;
  private boolean contested = false;
  private boolean firstBlockConfirmed = false;
  private boolean lastBlockConfirmed = false;
  private boolean awaitingBlocks = false;

  private final List<SignedBeaconBlock> blocks = new ArrayList<>();
  private final Map<Bytes32, List<BlobSidecar>> blobSidecarsByBlockRoot = new HashMap<>();

  SyncSourceBatch(
      final EventThread eventThread,
      final BlobSidecarManager blobSidecarManager,
      final SyncSourceSelector syncSourceProvider,
      final ConflictResolutionStrategy conflictResolutionStrategy,
      final TargetChain targetChain,
      final UInt64 firstSlot,
      final UInt64 count) {
    checkArgument(
        count.isGreaterThanOrEqualTo(UInt64.ONE), "Must include at least one slot in a batch");
    this.eventThread = eventThread;
    this.blobSidecarManager = blobSidecarManager;
    this.syncSourceProvider = syncSourceProvider;
    this.conflictResolutionStrategy = conflictResolutionStrategy;
    this.targetChain = targetChain;
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
  public Map<Bytes32, List<BlobSidecar>> getBlobSidecarsByBlockRoot() {
    return blobSidecarsByBlockRoot;
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

  public void markAsInconsistent() {
    currentSyncSource.ifPresent(
        source -> conflictResolutionStrategy.reportInconsistentBatch(this, source));
  }

  @Override
  public void requestMoreBlocks(final Runnable callback) {
    checkState(
        !isComplete() || isContested(), "Attempting to request more blocks from a complete batch");
    final BlockRequestHandler blockRequestHandler = new BlockRequestHandler();
    final UInt64 startSlot =
        getLastBlock().map(SignedBeaconBlock::getSlot).map(UInt64::increment).orElse(firstSlot);
    final UInt64 remainingSlots = count.minus(startSlot.minus(firstSlot));
    final UInt64 endSlot = startSlot.plus(remainingSlots).minus(UInt64.ONE);

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

    final SafeFuture<Void> blobSidecarsRequest;
    final Optional<BlobSidecarRequestHandler> maybeBlobSidecarRequestHandler;

    if (blobSidecarManager.isAvailabilityRequiredAtSlot(endSlot)) {
      LOG.debug(
          "Requesting blob sidecars for {} slots starting at {} from peer {}",
          remainingSlots,
          startSlot,
          syncSource);
      final BlobSidecarRequestHandler blobSidecarRequestHandler = new BlobSidecarRequestHandler();
      maybeBlobSidecarRequestHandler = Optional.of(blobSidecarRequestHandler);
      blobSidecarsRequest =
          syncSource.requestBlobSidecarsByRange(
              startSlot, remainingSlots, blobSidecarRequestHandler);
    } else {
      maybeBlobSidecarRequestHandler = Optional.empty();
      blobSidecarsRequest = SafeFuture.COMPLETE;
    }

    LOG.debug(
        "Requesting blocks for {} slots starting at {} from peer {}",
        remainingSlots,
        startSlot,
        syncSource);

    final SafeFuture<Void> blocksRequest =
        syncSource.requestBlocksByRange(startSlot, remainingSlots, blockRequestHandler);

    SafeFuture.allOfFailFast(blocksRequest, blobSidecarsRequest)
        .thenRunAsync(
            () -> onRequestComplete(blockRequestHandler, maybeBlobSidecarRequestHandler),
            eventThread)
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
    blobSidecarsByBlockRoot.clear();
  }

  private void onRequestComplete(
      final BlockRequestHandler blockRequestHandler,
      final Optional<BlobSidecarRequestHandler> maybeBlobSidecarRequestHandler) {
    eventThread.checkOnEventThread();
    final List<SignedBeaconBlock> newBlocks = blockRequestHandler.complete();

    awaitingBlocks = false;

    if (!validateNewBlocks(newBlocks)) {
      markAsInvalid();
      return;
    }
    blocks.addAll(newBlocks);

    if (maybeBlobSidecarRequestHandler.isPresent()) {
      final Map<Bytes32, List<BlobSidecar>> newBlobSidecarsByBlockRoot =
          maybeBlobSidecarRequestHandler.get().complete();
      if (!validateNewBlobSidecars(newBlocks, newBlobSidecarsByBlockRoot)) {
        markAsInvalid();
        return;
      }
      blobSidecarsByBlockRoot.putAll(newBlobSidecarsByBlockRoot);
    }

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

  private boolean validateNewBlocks(final List<SignedBeaconBlock> newBlocks) {
    if (blocks.isEmpty() || newBlocks.isEmpty()) {
      return true;
    }
    final SignedBeaconBlock previousBlock = blocks.get(blocks.size() - 1);
    final SignedBeaconBlock firstNewBlock = newBlocks.get(0);
    if (!firstNewBlock.getParentRoot().equals(previousBlock.getRoot())) {
      LOG.debug(
          "Marking batch invalid because new blocks do not form a chain with previous blocks");
      return false;
    }
    return true;
  }

  private boolean validateNewBlobSidecars(
      final List<SignedBeaconBlock> newBlocks,
      final Map<Bytes32, List<BlobSidecar>> newBlobSidecarsByBlockRoot) {
    final Set<Bytes32> blockRootsWithKzgCommitments = new HashSet<>(newBlocks.size());
    for (final SignedBeaconBlock block : newBlocks) {
      final Bytes32 blockRoot = block.getRoot();
      final List<BlobSidecar> blobSidecars =
          newBlobSidecarsByBlockRoot.getOrDefault(blockRoot, List.of());
      final int numberOfKzgCommitments =
          block
              .getMessage()
              .getBody()
              .toVersionDeneb()
              .map(BeaconBlockBodyDeneb::getBlobKzgCommitments)
              .map(SszList::size)
              .orElse(0);
      if (numberOfKzgCommitments > 0) {
        blockRootsWithKzgCommitments.add(blockRoot);
      }
      if (blobSidecars.size() != numberOfKzgCommitments) {
        LOG.debug(
            "Marking batch invalid because {} blob sidecars were received, but the number of KZG commitments in a block ({}) were {}",
            blobSidecars.size(),
            blockRoot,
            numberOfKzgCommitments);
        return false;
      }
      final UInt64 blockSlot = block.getSlot();
      for (final BlobSidecar blobSidecar : blobSidecars) {
        if (!blobSidecar.getSlot().equals(blockSlot)) {
          LOG.debug(
              "Marking batch invalid because blob sidecar for root {} was received with slot {} which is different than the block slot {}",
              blockRoot,
              blobSidecar.getSlot(),
              blockSlot);
          return false;
        }
      }
    }

    final SetView<Bytes32> unexpectedBlobSidecarsBlockRoots =
        Sets.difference(newBlobSidecarsByBlockRoot.keySet(), blockRootsWithKzgCommitments);
    if (!unexpectedBlobSidecarsBlockRoots.isEmpty()) {
      LOG.debug(
          "Marking batch inconsistent because unexpected blob sidecars with roots {} were received",
          unexpectedBlobSidecarsBlockRoots);
      markAsInconsistent();
      // clearing the unexpected blob sidecars from the batch
      unexpectedBlobSidecarsBlockRoots.immutableCopy().forEach(newBlobSidecarsByBlockRoot::remove);
    }

    return true;
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

  private static class BlobSidecarRequestHandler implements RpcResponseListener<BlobSidecar> {
    private final Map<Bytes32, List<BlobSidecar>> blobSidecarsByBlockRoot = new HashMap<>();

    @Override
    public SafeFuture<?> onResponse(final BlobSidecar blobSidecar) {
      final List<BlobSidecar> blobSidecars =
          blobSidecarsByBlockRoot.computeIfAbsent(
              blobSidecar.getBlockRoot(), __ -> new ArrayList<>());
      blobSidecars.add(blobSidecar);
      return SafeFuture.COMPLETE;
    }

    public Map<Bytes32, List<BlobSidecar>> complete() {
      return blobSidecarsByBlockRoot;
    }
  }
}
