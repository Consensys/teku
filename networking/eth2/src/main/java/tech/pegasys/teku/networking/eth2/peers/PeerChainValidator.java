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

package tech.pegasys.teku.networking.eth2.peers;

import static tech.pegasys.teku.core.ForkChoiceUtil.get_current_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.networking.p2p.peer.DisconnectRequestHandler.DisconnectReason;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.Constants;

public class PeerChainValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData storageClient;
  private final StorageQueryChannel historicalChainData;
  private final Eth2Peer peer;
  private final AtomicBoolean hasRun = new AtomicBoolean(false);
  private final PeerStatus status;
  private SafeFuture<Boolean> result;

  private PeerChainValidator(
      final RecentChainData storageClient,
      final StorageQueryChannel historicalChainData,
      final Eth2Peer peer,
      final PeerStatus status) {
    this.storageClient = storageClient;
    this.historicalChainData = historicalChainData;
    this.peer = peer;
    this.status = status;
  }

  public static PeerChainValidator create(
      final RecentChainData storageClient,
      final StorageQueryChannel historicalChainData,
      final Eth2Peer peer,
      final PeerStatus status) {
    return new PeerChainValidator(storageClient, historicalChainData, peer, status);
  }

  public SafeFuture<Boolean> run() {
    if (hasRun.compareAndSet(false, true)) {
      result = executeCheck();
    }
    return result;
  }

  private SafeFuture<Boolean> executeCheck() {
    LOG.trace("Validate chain of peer: {}", peer);
    return checkRemoteChain()
        .thenApply(
            isValid -> {
              if (!isValid) {
                // We are not on the same chain
                LOG.trace("Disconnecting peer on different chain: {}", peer);
                peer.disconnectCleanly(DisconnectReason.IRRELEVANT_NETWORK);
              } else {
                LOG.trace("Validated peer's chain: {}", peer);
                peer.markChainValidated();
              }
              return isValid;
            })
        .exceptionally(
            err -> {
              LOG.debug("Unable to validate peer's chain, disconnecting: " + peer, err);
              peer.disconnectCleanly(DisconnectReason.UNABLE_TO_VERIFY_NETWORK);
              return false;
            });
  }

  private SafeFuture<Boolean> checkRemoteChain() {
    // Shortcut checks if our node or our peer has not reached genesis
    if (storageClient.isPreGenesis()) {
      // If we haven't reached genesis, accept our peer at this point
      LOG.trace("Validating peer pre-genesis, skip finalized block checks for peer {}", peer);
      return SafeFuture.completedFuture(true);
    } else if (PeerStatus.isPreGenesisStatus(status)) {
      // Our peer hasn't reached genesis, accept them for now
      LOG.trace("Peer has not reached genesis, skip finalized block checks for peer {}", peer);
      return SafeFuture.completedFuture(true);
    }

    // Check fork compatibility
    Bytes4 expectedForkDigest = storageClient.getHeadForkInfo().orElseThrow().getForkDigest();
    if (!Objects.equals(expectedForkDigest, status.getForkDigest())) {
      LOG.trace(
          "Peer's fork ({}) differs from our fork ({}): {}",
          status.getForkDigest(),
          expectedForkDigest,
          peer);
      return SafeFuture.completedFuture(false);
    }
    final UnsignedLong remoteFinalizedEpoch = status.getFinalizedEpoch();
    // Only require fork digest to match if only genesis is finalized
    if (remoteFinalizedEpoch.equals(UnsignedLong.ZERO)) {
      return SafeFuture.completedFuture(true);
    }

    // Check finalized checkpoint compatibility
    final Checkpoint finalizedCheckpoint =
        storageClient.getBestState().orElseThrow().getFinalized_checkpoint();
    final UnsignedLong finalizedEpoch = finalizedCheckpoint.getEpoch();
    final UnsignedLong currentEpoch = getCurrentEpoch();

    // Make sure remote finalized epoch is reasonable
    if (remoteEpochIsInvalid(currentEpoch, remoteFinalizedEpoch)) {
      LOG.debug(
          "Peer is advertising invalid finalized epoch {} which is at or ahead of our current epoch {}: {}",
          remoteFinalizedEpoch,
          currentEpoch,
          peer);
      return SafeFuture.completedFuture(false);
    }

    // Check whether finalized checkpoints are compatible
    if (finalizedEpoch.compareTo(remoteFinalizedEpoch) == 0) {
      LOG.trace(
          "Finalized epoch for peer {} matches our own finalized epoch {}, verify blocks roots match",
          peer.getId(),
          finalizedEpoch);
      return verifyFinalizedCheckpointsAreTheSame(finalizedCheckpoint);
    } else if (finalizedEpoch.compareTo(remoteFinalizedEpoch) > 0) {
      // We're ahead of our peer, check that we agree with our peer's finalized epoch
      LOG.trace(
          "Our finalized epoch {} is ahead of our peer's ({}) finalized epoch {}, check that we consider our peer's finalized block to be canonical.",
          finalizedEpoch,
          peer.getId(),
          remoteFinalizedEpoch);
      return verifyPeersFinalizedCheckpointIsCanonical();
    } else {
      // Our peer is ahead of us, check that they agree on our finalized epoch
      LOG.trace(
          "Our finalized epoch {} is behind of our peer's ({}) finalized epoch {}, check that our peer considers our latest finalized block to be canonical.",
          finalizedEpoch,
          peer.getId(),
          remoteFinalizedEpoch);
      return verifyPeerAgreesWithOurFinalizedCheckpoint(finalizedCheckpoint);
    }
  }

  private UnsignedLong getCurrentEpoch() {
    final UnsignedLong currentSlot = get_current_slot(storageClient.getStore());
    return compute_epoch_at_slot(currentSlot);
  }

  private boolean remoteEpochIsInvalid(
      final UnsignedLong currentEpoch, final UnsignedLong remoteFinalizedEpoch) {
    // Remote finalized epoch is invalid if it is from the future
    return remoteFinalizedEpoch.compareTo(currentEpoch) > 0
        // Remote finalized epoch is invalid if is from the current epoch (unless we're at genesis)
        || (remoteFinalizedEpoch.compareTo(currentEpoch) == 0
            && !remoteFinalizedEpoch.equals(UnsignedLong.valueOf(Constants.GENESIS_EPOCH)));
  }

  private SafeFuture<Boolean> verifyFinalizedCheckpointsAreTheSame(Checkpoint finalizedCheckpoint) {
    final boolean chainsAreConsistent =
        Objects.equals(finalizedCheckpoint.getRoot(), status.getFinalizedRoot());
    return SafeFuture.completedFuture(chainsAreConsistent);
  }

  private SafeFuture<Boolean> verifyPeersFinalizedCheckpointIsCanonical() {
    final Checkpoint remoteFinalizedCheckpoint = status.getFinalizedCheckpoint();
    final UnsignedLong remoteFinalizedSlot = remoteFinalizedCheckpoint.getEpochStartSlot();
    return historicalChainData
        .getLatestFinalizedBlockAtSlot(remoteFinalizedSlot)
        .thenApply(maybeBlock -> toBlock(remoteFinalizedSlot, maybeBlock))
        .thenApply((block) -> validateBlockRootsMatch(block, status.getFinalizedRoot()));
  }

  private SafeFuture<Boolean> verifyPeerAgreesWithOurFinalizedCheckpoint(
      Checkpoint finalizedCheckpoint) {
    final UnsignedLong finalizedEpochSlot = finalizedCheckpoint.getEpochStartSlot();
    if (finalizedEpochSlot.equals(UnsignedLong.valueOf(Constants.GENESIS_SLOT))) {
      // Assume that our genesis blocks match because we've already verified the fork
      // digest.
      return SafeFuture.completedFuture(true);
    }
    return historicalChainData
        .getLatestFinalizedBlockAtSlot(finalizedEpochSlot)
        .thenApply(maybeBlock -> blockToSlot(finalizedEpochSlot, maybeBlock))
        .thenCompose(
            blockSlot -> {
              if (blockSlot.equals(UnsignedLong.valueOf(Constants.GENESIS_SLOT))) {
                // Assume that our genesis blocks match because we've already verified the fork
                // digest. Need to repeat this check in case we finalized a later epoch without
                // producing blocks (eg the genesis block is still the one in effect at epoch 2)
                return SafeFuture.completedFuture(true);
              }
              return peer.requestBlockBySlot(blockSlot)
                  .thenApply(
                      block -> validateBlockRootsMatch(block, finalizedCheckpoint.getRoot()));
            });
  }

  private SignedBeaconBlock toBlock(
      UnsignedLong lookupSlot, Optional<SignedBeaconBlock> maybeBlock) {
    return maybeBlock.orElseThrow(
        () -> new IllegalStateException("Missing finalized block at slot " + lookupSlot));
  }

  private UnsignedLong blockToSlot(
      UnsignedLong lookupSlot, Optional<SignedBeaconBlock> maybeBlock) {
    return maybeBlock
        .map(SignedBeaconBlock::getSlot)
        .orElseThrow(
            () -> new IllegalStateException("Missing historical block for slot " + lookupSlot));
  }

  private boolean validateBlockRootsMatch(final SignedBeaconBlock block, final Bytes32 root) {
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    final boolean rootsMatch = Objects.equals(blockRoot, root);
    if (rootsMatch) {
      LOG.trace("Verified finalized blocks match for peer: {}", peer);
    } else {
      LOG.warn(
          "Detected peer with inconsistent finalized block at slot {} for peer {}.  Block roots {} and {} do not match",
          block.getSlot(),
          peer,
          blockRoot,
          root);
    }
    return rootsMatch;
  }
}
