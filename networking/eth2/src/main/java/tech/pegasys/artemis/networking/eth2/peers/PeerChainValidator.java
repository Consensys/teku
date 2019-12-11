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

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer.StatusData;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

public class PeerChainValidator {
  private final ChainStorageClient storageClient;
  private final Eth2Peer peer;
  private final AtomicBoolean hasRun = new AtomicBoolean(false);

  private PeerChainValidator(final ChainStorageClient storageClient, final Eth2Peer peer) {
    this.storageClient = storageClient;
    this.peer = peer;
  }

  public static PeerChainValidator create(
      final ChainStorageClient storageClient, final Eth2Peer peer) {
    return new PeerChainValidator(storageClient, peer);
  }

  public void run() {
    if (hasRun.compareAndSet(false, true)) {
      executeCheck();
    }
  }

  private void executeCheck() {
    checkRemoteChain()
        .whenComplete(
            (isValid, error) -> {
              if (error != null) {
                peer.sendGoodbye(GoodbyeMessage.REASON_UNABLE_TO_VERIFY_NETWORK);
                return;
              } else if (!isValid) {
                // We are not on the same chain
                peer.sendGoodbye(GoodbyeMessage.REASON_IRRELEVANT_NETWORK);
                return;
              }
              peer.markChainValidated();
            });
  }

  private CompletableFuture<Boolean> checkRemoteChain() {
    checkState(peer.hasStatus(), "Peer must have an associated status.");
    final StatusData peerStatus = peer.getStatus();

    final CompletableFuture<Boolean> isChainValid = new CompletableFuture<>();

    // Check fork compatibility
    Bytes4 expectedFork = storageClient.getForkAtSlot(peerStatus.getHeadSlot());
    if (!Objects.equals(expectedFork, peerStatus.getHeadForkVersion())) {
      isChainValid.complete(false);
      return isChainValid;
    }

    // Check finalized checkpoints are compatible
    final Checkpoint finalizedCheckpoint = storageClient.getStore().getFinalizedCheckpoint();
    final UnsignedLong finalizedEpoch = finalizedCheckpoint.getEpoch();
    final UnsignedLong remoteFinalizedEpoch = peerStatus.getFinalizedEpoch();

    if (finalizedEpoch.compareTo(remoteFinalizedEpoch) == 0) {
      final boolean chainsAreConsistent =
          Objects.equals(finalizedCheckpoint.getRoot(), peerStatus.getFinalizedRoot());
      isChainValid.complete(chainsAreConsistent);
    } else if (finalizedEpoch.compareTo(remoteFinalizedEpoch) > 0) {
      // We're ahead of our peer, check that we agree with our peer's finalized epoch
      final UnsignedLong remoteFinalizedSlot = compute_start_slot_at_epoch(remoteFinalizedEpoch);
      final boolean chainsAreConsistent =
          storageClient
              .getBlockAtOrPriorToSlot(remoteFinalizedSlot)
              .map(
                  block ->
                      Objects.equals(
                          block.signing_root("signature"), peerStatus.getFinalizedRoot()))
              // TODO - if we get no response, we need to look up the block by slot from cold
              // storage. For now, don't disconnect peers who are very far behind
              .orElse(true);
      isChainValid.complete(chainsAreConsistent);
    } else {
      // Our peer is ahead of us, check that they agree on our finalized epoch
      final UnsignedLong localFinalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);
      peer.requestBlockBySlot(peerStatus.getHeadRoot(), localFinalizedSlot)
          .thenApply(
              block ->
                  Objects.equals(block.signing_root("signature"), finalizedCheckpoint.getRoot()))
          .whenComplete(
              (res, error) -> {
                if (error != null) {
                  isChainValid.completeExceptionally(error);
                  return;
                }
                isChainValid.complete(res);
              });
    }
    return isChainValid;
  }
}
