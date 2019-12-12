/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.networking.eth2.peers;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer.StatusData;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

public class PeerChainValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final ChainStorageClient storageClient;
  private final HistoricalChainData historicalChainData;
  private final Eth2Peer peer;
  private final AtomicBoolean hasRun = new AtomicBoolean(false);
  private final StatusData status;

  private PeerChainValidator(
      final ChainStorageClient storageClient,
      final HistoricalChainData historicalChainData,
      final Eth2Peer peer,
      final StatusData status) {
    this.storageClient = storageClient;
    this.historicalChainData = historicalChainData;
    this.peer = peer;
    this.status = status;
  }

  public static PeerChainValidator create(
      final ChainStorageClient storageClient,
      final HistoricalChainData historicalChainData,
      final Eth2Peer peer,
      final StatusData status) {
    return new PeerChainValidator(storageClient, historicalChainData, peer, status);
  }

  public void run() {
    if (hasRun.compareAndSet(false, true)) {
      executeCheck();
    }
  }

  private void executeCheck() {
    LOG.trace("Validate chain of peer: {}", peer);
    checkRemoteChain()
        .whenComplete(
            (isValid, error) -> {
              if (error != null) {
                LOG.debug("Unable to validate peer's chain, disconnecting: " + peer, error);
                peer.sendGoodbye(GoodbyeMessage.REASON_UNABLE_TO_VERIFY_NETWORK);
              } else if (!isValid) {
                // We are not on the same chain
                LOG.trace("Disconnecting peer on different chain: {}", peer);
                peer.sendGoodbye(GoodbyeMessage.REASON_IRRELEVANT_NETWORK);
              } else {
                LOG.trace("Validated peer's chain: {}", peer);
                peer.markChainValidated();
              }
            });
  }

  private CompletableFuture<Boolean> checkRemoteChain() {
    // Check fork compatibility
    Bytes4 expectedFork = storageClient.getForkAtSlot(status.getHeadSlot());
    if (!Objects.equals(expectedFork, status.getHeadForkVersion())) {
      LOG.trace(
          "Peer's fork ({}) differs from our fork ({}): {}",
          status.getHeadForkVersion(),
          expectedFork,
          peer);
      return CompletableFuture.completedFuture(false);
    }

    // If we haven't reached genesis, accept our peer at this point
    if (storageClient.isPreGenesis()) {
      LOG.trace("Validating peer pre-genesis, skip finalized block checks for peer {}", peer);
      return CompletableFuture.completedFuture(true);
    }

    // Check whether finalized checkpoints are compatible
    final Checkpoint finalizedCheckpoint = storageClient.getStore().getFinalizedCheckpoint();
    final UnsignedLong finalizedEpoch = finalizedCheckpoint.getEpoch();
    final UnsignedLong remoteFinalizedEpoch = status.getFinalizedEpoch();

    if (finalizedEpoch.compareTo(remoteFinalizedEpoch) == 0) {
      return verifyFinalizedCheckpointsAreTheSame(finalizedCheckpoint);
    } else if (finalizedEpoch.compareTo(remoteFinalizedEpoch) > 0) {
      // We're ahead of our peer, check that we agree with our peer's finalized epoch
      return verifyPeersFinalizedCheckpointIsCanonical();
    } else {
      // Our peer is ahead of us, check that they agree on our finalized epoch
      return verifyPeerAgreesWithOurFinalizedCheckpoint(finalizedCheckpoint);
    }
  }

  private CompletableFuture<Boolean> verifyFinalizedCheckpointsAreTheSame(
      Checkpoint finalizedCheckpoint) {
    final boolean chainsAreConsistent =
        Objects.equals(finalizedCheckpoint.getRoot(), status.getFinalizedRoot());
    return CompletableFuture.completedFuture(chainsAreConsistent);
  }

  private CompletableFuture<Boolean> verifyPeersFinalizedCheckpointIsCanonical() {
    final UnsignedLong remoteFinalizedEpoch = status.getFinalizedEpoch();
    final UnsignedLong remoteFinalizedSlot = compute_start_slot_at_epoch(remoteFinalizedEpoch);
    return historicalChainData
        // TODO - we need to get the block at or before this slot
        .getBlockBySlot(remoteFinalizedSlot)
        .thenApply(maybeBlock -> toBlock(remoteFinalizedSlot, maybeBlock))
        .thenApply((block) -> validateBlockRootsMatch(block, status.getFinalizedRoot()));
  }

  private CompletableFuture<Boolean> verifyPeerAgreesWithOurFinalizedCheckpoint(
      Checkpoint finalizedCheckpoint) {
    final UnsignedLong finalizedEpochSlot =
        compute_start_slot_at_epoch(finalizedCheckpoint.getEpoch());

    return historicalChainData
        // TODO - we need to get the block at or before this slot
        .getBlockBySlot(finalizedEpochSlot)
        .thenApply(maybeBlock -> blockToSlot(finalizedEpochSlot, maybeBlock))
        .thenCompose(blockSlot -> peer.requestBlockBySlot(status.getHeadRoot(), blockSlot))
        .thenApply(block -> validateBlockRootsMatch(block, finalizedCheckpoint.getRoot()));
  }

  private BeaconBlock toBlock(UnsignedLong lookupSlot, Optional<BeaconBlock> maybeBlock) {
    return maybeBlock.orElseThrow(
        () -> new IllegalStateException("Missing finalized block at slot " + lookupSlot));
  }

  private UnsignedLong blockToSlot(UnsignedLong lookupSlot, Optional<BeaconBlock> maybeBlock) {
    return maybeBlock
        .map(BeaconBlock::getSlot)
        .orElseThrow(
            () -> new IllegalStateException("Missing historical block for slot " + lookupSlot));
  }

  private boolean validateBlockRootsMatch(final BeaconBlock block, final Bytes32 root) {
    final Bytes32 blockRoot = block.signing_root("signature");
    final boolean rootsMatch = Objects.equals(blockRoot, root);
    if (rootsMatch) {
      LOG.trace("Verified finalized blocks match for peer: {}", peer);
    } else {
      LOG.warn(
          "Detected peer with inconsistent finalized block at slot {}: {}", block.getSlot(), peer);
    }
    return rootsMatch;
  }
}
