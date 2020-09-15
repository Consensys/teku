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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;

public class SyncSourceBatch implements Batch {
  private static final Logger LOG = LogManager.getLogger();

  private final EventThread eventThread;
  private final Supplier<SyncSource> syncSourceProvider;
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

  SyncSourceBatch(
      final EventThread eventThread,
      final Supplier<SyncSource> syncSourceProvider,
      final TargetChain targetChain,
      final UInt64 firstSlot,
      final UInt64 count) {
    checkArgument(
        count.isGreaterThanOrEqualTo(UInt64.ONE), "Must include at least one slot in a batch");
    this.eventThread = eventThread;
    this.syncSourceProvider = syncSourceProvider;
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
    firstBlockConfirmed = true;
  }

  @Override
  public void markLastBlockConfirmed() {
    lastBlockConfirmed = true;
  }

  @Override
  public void markAsContested() {
    contested = true;
    // TODO: Need to apply some form of conflict resolution strategy.
    // This code gets sync working but is pretty brute force and not well thought out
    //    currentSyncSource = Optional.empty();
    //    reset();
  }

  @Override
  public void markAsInvalid() {}

  @Override
  public void requestMoreBlocks(final Runnable callback) {
    checkState(
        !isComplete() || isContested(), "Attempting to request more blocks from a complete batch");
    final RequestHandler requestHandler = new RequestHandler();
    final UInt64 startSlot =
        getLastBlock().map(SignedBeaconBlock::getSlot).map(UInt64::increment).orElse(firstSlot);
    final UInt64 remainingSlots = count.minus(startSlot.minus(firstSlot));
    if (currentSyncSource.isEmpty()) {
      currentSyncSource = Optional.of(syncSourceProvider.get());
    }
    awaitingBlocks = true;
    final SyncSource syncSource = currentSyncSource.orElseThrow();
    LOG.debug(
        "Requesting {} slots starting at {} from peer {}", remainingSlots, startSlot, syncSource);
    syncSource
        .requestBlocksByRange(startSlot, remainingSlots, UInt64.ONE, requestHandler)
        .thenRunAsync(() -> onRequestComplete(requestHandler), eventThread)
        .handleAsync(
            (__, error) -> {
              if (error != null) {
                handleRequestErrors(error);
              }
              callback.run();
              return null;
            },
            eventThread)
        .reportExceptions();
  }

  private void handleRequestErrors(final Throwable error) {
    eventThread.checkOnEventThread();
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof PeerDisconnectedException) {
      LOG.debug("Failed to retrieve blocks because peer disconnected", error);
      currentSyncSource = Optional.empty();
    } else {
      LOG.debug("Error while requesting blocks", error);
    }
    reset();
    awaitingBlocks = false;
  }

  private void reset() {
    complete = false;
    contested = false;
    firstBlockConfirmed = false;
    lastBlockConfirmed = false;
    blocks.clear();
  }

  private void onRequestComplete(final RequestHandler requestHandler) {
    eventThread.checkOnEventThread();
    final List<SignedBeaconBlock> newBlocks = requestHandler.complete();
    blocks.addAll(newBlocks);
    if (newBlocks.isEmpty()
        || newBlocks.get(newBlocks.size() - 1).getSlot().equals(getLastSlot())) {
      complete = true;
    }
    awaitingBlocks = false;
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
        .add("firstBlock", getFirstBlock().map(this::formatBlock).orElse("<none>"))
        .add("lastBlock", getLastBlock().map(this::formatBlock).orElse("<none>"))
        .toString();
  }

  private String formatBlock(final SignedBeaconBlock block) {
    return LogFormatter.formatBlock(block.getSlot(), block.getRoot());
  }

  private static class RequestHandler implements ResponseStreamListener<SignedBeaconBlock> {
    private final List<SignedBeaconBlock> blocks = new ArrayList<>();

    @Override
    public SafeFuture<?> onResponse(final SignedBeaconBlock response) {
      // TODO: Verify the blocks form a chain internally and with previous blocks in this batch
      blocks.add(response);
      return SafeFuture.COMPLETE;
    }

    public List<SignedBeaconBlock> complete() {
      return blocks;
    }
  }
}
