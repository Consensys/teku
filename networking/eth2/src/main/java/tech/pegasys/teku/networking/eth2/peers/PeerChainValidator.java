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

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.util.config.Constants;

public class PeerChainValidator {
  private static final Logger LOG = LogManager.getLogger();

  // If we're missing historical blocks and are unable to verify a peer's finalized checkpoint is on
  // our chain, this boolean determines whether we should allow them to connect or not.
  // TODO(#3064) - We should implement a historical sync backwards so we can support peers who are
  //  behind our anchorPoint.  Disabling this for now since we won't be able to serve them blocks
  //  they need.
  private static final boolean ALLOW_NODES_PRIOR_TO_LOCAL_ANCHORPOINT_TO_CONNECT = false;

  private final CombinedChainDataClient chainDataClient;
  private final Counter validationStartedCounter;
  private final Counter chainValidCounter;
  private final Counter chainInvalidCounter;
  private final Counter validationErrorCounter;

  private final Optional<Checkpoint> requiredCheckpoint;
  private final AtomicBoolean requiredCheckpointVerified = new AtomicBoolean(false);
  private final boolean allowNodesPriorToLocalAnchorPointToConnect;

  @VisibleForTesting
  PeerChainValidator(
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient chainDataClient,
      final Optional<Checkpoint> requiredCheckpoint,
      final boolean allowNodesPriorToLocalAnchorPointToConnect) {
    this.chainDataClient = chainDataClient;
    this.requiredCheckpoint = requiredCheckpoint;
    this.allowNodesPriorToLocalAnchorPointToConnect = allowNodesPriorToLocalAnchorPointToConnect;

    final LabelledMetric<Counter> validationCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "peer_chain_validation_attempts",
            "Number of peers chain verification has been performed on",
            "status");
    validationStartedCounter = validationCounter.labels("started");
    chainValidCounter = validationCounter.labels("valid");
    chainInvalidCounter = validationCounter.labels("invalid");
    validationErrorCounter = validationCounter.labels("error");
  }

  public static PeerChainValidator create(
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient chainDataClient,
      final Optional<Checkpoint> requiredCheckpoint) {
    return new PeerChainValidator(
        metricsSystem,
        chainDataClient,
        requiredCheckpoint,
        ALLOW_NODES_PRIOR_TO_LOCAL_ANCHORPOINT_TO_CONNECT);
  }

  public SafeFuture<Boolean> validate(final Eth2Peer peer, final PeerStatus newStatus) {
    LOG.trace("Validate chain of peer: {}", peer.getId());
    validationStartedCounter.inc();
    return isRemoteChainValid(peer, newStatus)
        .thenApply(
            isValid -> {
              if (!isValid) {
                // We are not on the same chain
                LOG.trace("Disconnecting peer on different chain: {}", peer.getId());
                chainInvalidCounter.inc();
                peer.disconnectCleanly(DisconnectReason.IRRELEVANT_NETWORK).reportExceptions();
              } else {
                LOG.trace("Validated peer's chain: {}", peer.getId());
                chainValidCounter.inc();
              }
              return isValid;
            })
        .exceptionally(
            err -> {
              LOG.debug("Unable to validate peer's chain, disconnecting {}", peer.getId(), err);
              validationErrorCounter.inc();
              peer.disconnectCleanly(DisconnectReason.UNABLE_TO_VERIFY_NETWORK).reportExceptions();
              return false;
            });
  }

  private SafeFuture<Boolean> isRemoteChainValid(final Eth2Peer peer, final PeerStatus status) {
    if (!isForkValid(peer, status)) {
      return SafeFuture.completedFuture(false);
    }

    // Skip remaining checks if only genesis is finalized
    if (status.getFinalizedEpoch().equals(UInt64.ZERO)) {
      return SafeFuture.completedFuture(true);
    }

    return isConsistentWithRequiredCheckpoint(peer, status)
        .thenCompose(
            isValid -> {
              if (!isValid) {
                // Short-circuit if we know the chain is invalid
                LOG.warn(
                    "Peer {} failed validation against required checkpoint {}",
                    peer.getId(),
                    requiredCheckpoint);
                return SafeFuture.completedFuture(isValid);
              }

              return isFinalizedCheckpointValid(peer, status);
            });
  }

  private boolean isForkValid(final Eth2Peer peer, final PeerStatus status) {
    Bytes4 expectedForkDigest = chainDataClient.getHeadForkInfo().orElseThrow().getForkDigest();
    if (!Objects.equals(expectedForkDigest, status.getForkDigest())) {
      LOG.trace(
          "Peer's fork ({}) differs from our fork ({}): {}",
          status.getForkDigest(),
          expectedForkDigest,
          peer.getId());
      return false;
    }

    return true;
  }

  private SafeFuture<Boolean> isConsistentWithRequiredCheckpoint(
      final Eth2Peer peer, final PeerStatus status) {
    if (requiredCheckpointVerified.get()) {
      return SafeFuture.completedFuture(true);
    }
    if (requiredCheckpoint.isEmpty()) {
      requiredCheckpointVerified.set(true);
      return SafeFuture.completedFuture(true);
    }

    final Checkpoint checkpointToVerify = requiredCheckpoint.get();
    if (status.getFinalizedCheckpoint().getEpoch().isLessThan(checkpointToVerify.getEpoch())) {
      // Peer hasn't finalized the required checkpoint, so defer check
      return SafeFuture.completedFuture(true);
    } else if (status.getFinalizedCheckpoint().getEpoch().equals(checkpointToVerify.getEpoch())) {
      LOG.trace(
          "Validate peer's ({}) finalized checkpoint {} matches required checkpoint {}",
          peer.getId(),
          status.getFinalizedCheckpoint(),
          checkpointToVerify);
      // Peer is at the required checkpoint, check for consistency
      final boolean blockMatches = status.getFinalizedCheckpoint().equals(checkpointToVerify);
      requiredCheckpointVerified.set(blockMatches);
      return SafeFuture.completedFuture(blockMatches);
    } else {
      // Peer has finalized the required checkpoint in the past, request this block to check
      // consistency
      LOG.trace(
          "Request required checkpoint block from peer {}: {}", peer.getId(), checkpointToVerify);
      return peer.requestBlockByRoot(checkpointToVerify.getRoot())
          // When requesting block by root, there is no explicit guarantee that the block is
          // canonical.
          // So, double-check by requesting the block by slot to make sure the peer considers this
          // block canonical.
          .thenCompose(
              maybeBlock ->
                  maybeBlock
                      .map(
                          b ->
                              peer.requestBlockBySlot(b.getSlot())
                                  .thenApply(
                                      blockBySlot -> {
                                        final boolean blockMatches =
                                            blockBySlot.isPresent()
                                                && blockBySlot
                                                    .get()
                                                    .getRoot()
                                                    .equals(checkpointToVerify.getRoot());
                                        requiredCheckpointVerified.set(blockMatches);
                                        return blockMatches;
                                      }))
                      .orElseGet(() -> SafeFuture.completedFuture(false)));
    }
  }

  private SafeFuture<Boolean> isFinalizedCheckpointValid(
      final Eth2Peer peer, final PeerStatus status) {
    final UInt64 remoteFinalizedEpoch = status.getFinalizedEpoch();
    final Checkpoint localFinalizedCheckpoint = chainDataClient.getStore().getFinalizedCheckpoint();
    final UInt64 localFinalizedEpoch = localFinalizedCheckpoint.getEpoch();
    final UInt64 currentEpoch = chainDataClient.getCurrentEpoch();

    // Make sure remote finalized epoch is reasonable
    if (remoteEpochIsInvalid(currentEpoch, remoteFinalizedEpoch)) {
      LOG.debug(
          "Peer is advertising invalid finalized epoch {} which is at or ahead of our current epoch {}: {}",
          remoteFinalizedEpoch,
          currentEpoch,
          peer.getId());
      return SafeFuture.completedFuture(false);
    }

    // Check whether finalized checkpoints are compatible
    if (localFinalizedEpoch.equals(remoteFinalizedEpoch)) {
      LOG.trace(
          "Finalized epoch for peer {} matches our own finalized epoch {}, verify blocks roots match",
          peer.getId(),
          localFinalizedEpoch);
      return verifyFinalizedCheckpointsAreTheSame(localFinalizedCheckpoint, status);
    } else if (localFinalizedEpoch.isGreaterThan(remoteFinalizedEpoch)) {
      // We're ahead of our peer, check that we agree with our peer's finalized epoch
      LOG.trace(
          "Our finalized epoch {} is ahead of our peer's ({}) finalized epoch {}, check that we consider our peer's finalized block to be canonical.",
          localFinalizedEpoch,
          peer.getId(),
          remoteFinalizedEpoch);
      return verifyPeersFinalizedCheckpointIsCanonical(peer, status);
    } else {
      // Our peer is ahead of us, check that they agree on our finalized epoch
      LOG.trace(
          "Our finalized epoch {} is behind of our peer's ({}) finalized epoch {}, check that our peer considers our latest finalized block to be canonical.",
          localFinalizedEpoch,
          peer.getId(),
          remoteFinalizedEpoch);
      return verifyPeerAgreesWithOurFinalizedCheckpoint(peer, localFinalizedCheckpoint);
    }
  }

  private boolean remoteEpochIsInvalid(
      final UInt64 currentEpoch, final UInt64 remoteFinalizedEpoch) {
    // Remote finalized epoch is invalid if it is from the future
    return remoteFinalizedEpoch.compareTo(currentEpoch) > 0
        // Remote finalized epoch is invalid if is from the current epoch (unless we're at genesis)
        || (remoteFinalizedEpoch.compareTo(currentEpoch) == 0
            && !remoteFinalizedEpoch.equals(UInt64.valueOf(Constants.GENESIS_EPOCH)));
  }

  private SafeFuture<Boolean> verifyFinalizedCheckpointsAreTheSame(
      Checkpoint finalizedCheckpoint, final PeerStatus status) {
    final boolean chainsAreConsistent =
        Objects.equals(finalizedCheckpoint.getRoot(), status.getFinalizedRoot());
    return SafeFuture.completedFuture(chainsAreConsistent);
  }

  private SafeFuture<Boolean> verifyPeersFinalizedCheckpointIsCanonical(
      final Eth2Peer peer, final PeerStatus status) {
    final Checkpoint remoteFinalizedCheckpoint = status.getFinalizedCheckpoint();
    final UInt64 remoteFinalizedSlot = remoteFinalizedCheckpoint.getEpochStartSlot();
    return chainDataClient
        .getBlockInEffectAtSlot(remoteFinalizedSlot)
        .thenApply(
            maybeBlock ->
                maybeBlock
                    .map(block -> validateBlockRootsMatch(peer, block, status.getFinalizedRoot()))
                    .orElseGet(
                        () -> {
                          if (allowNodesPriorToLocalAnchorPointToConnect) {
                            LOG.trace(
                                "Missing finalized historical block corresponding to peer's latest finalized checkpoint.  Allow peer to connect without verifying remote finalized checkpoint.");
                          } else {
                            LOG.trace(
                                "Missing finalized historical block corresponding to peer's latest finalized checkpoint.  Drop peer connection.");
                          }
                          return allowNodesPriorToLocalAnchorPointToConnect;
                        }));
  }

  private SafeFuture<Boolean> verifyPeerAgreesWithOurFinalizedCheckpoint(
      final Eth2Peer peer, Checkpoint finalizedCheckpoint) {
    final UInt64 finalizedEpochSlot = finalizedCheckpoint.getEpochStartSlot();
    if (finalizedEpochSlot.equals(UInt64.valueOf(Constants.GENESIS_SLOT))) {
      // Assume that our genesis blocks match because we've already verified the fork
      // digest.
      return SafeFuture.completedFuture(true);
    }
    return chainDataClient
        .getBlockInEffectAtSlot(finalizedEpochSlot)
        .thenApply(maybeBlock -> blockToSlot(finalizedEpochSlot, maybeBlock))
        .thenCompose(
            blockSlot -> {
              if (blockSlot.equals(UInt64.valueOf(Constants.GENESIS_SLOT))) {
                // Assume that our genesis blocks match because we've already verified the fork
                // digest. Need to repeat this check in case we finalized a later epoch without
                // producing blocks (eg the genesis block is still the one in effect at epoch 2)
                return SafeFuture.completedFuture(true);
              }
              return peer.requestBlockBySlot(blockSlot)
                  .thenApply(
                      block -> validateBlockRootsMatch(peer, block, finalizedCheckpoint.getRoot()));
            });
  }

  private UInt64 blockToSlot(UInt64 lookupSlot, Optional<SignedBeaconBlock> maybeBlock) {
    return maybeBlock
        .map(SignedBeaconBlock::getSlot)
        .orElseThrow(
            () -> new IllegalStateException("Missing historical block for slot " + lookupSlot));
  }

  private boolean validateBlockRootsMatch(
      final Eth2Peer peer, final Optional<SignedBeaconBlock> mabyeBlock, final Bytes32 root) {
    if (mabyeBlock.isEmpty()) {
      LOG.debug("Peer validation failed because it did not provide requested finalized block");
      return false;
    }
    return validateBlockRootsMatch(peer, mabyeBlock.get(), root);
  }

  private boolean validateBlockRootsMatch(
      final Eth2Peer peer, final SignedBeaconBlock block, final Bytes32 root) {
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    final boolean rootsMatch = Objects.equals(blockRoot, root);
    if (rootsMatch) {
      LOG.trace("Verified finalized blocks match for peer: {}", peer.getId());
    } else {
      LOG.warn(
          "Detected peer with inconsistent finalized block at slot {} for peer {}.  Block roots {} and {} do not match",
          block.getSlot(),
          peer.getId(),
          blockRoot,
          root);
    }
    return rootsMatch;
  }
}
