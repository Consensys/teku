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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer.StatusData;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.config.Constants;

public class PeerChainValidatorTest {

  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final Store store = mock(Store.class);
  private final ChainStorageClient storageClient = mock(ChainStorageClient.class);
  private final HistoricalChainData historicalChainData = mock(HistoricalChainData.class);

  private final Bytes4 remoteFork = new Bytes4(Bytes.fromHexString("0x1234", 4));
  private final Bytes4 otherFork = new Bytes4(Bytes.fromHexString("0x3333", 4));

  private final UnsignedLong remoteFinalizedEpoch = UnsignedLong.valueOf(10L);
  private final UnsignedLong earlierEpoch = UnsignedLong.valueOf(8L);
  private final UnsignedLong laterEpoch = UnsignedLong.valueOf(12L);

  private final UnsignedLong remoteFinalizedEpochSlot =
      compute_start_slot_at_epoch(remoteFinalizedEpoch);
  private final UnsignedLong earlierEpochSlot = compute_start_slot_at_epoch(earlierEpoch);
  private final UnsignedLong laterEpochSlot = compute_start_slot_at_epoch(laterEpoch);

  // Offset slots from epoch to simulate skipped blocks
  private final UnsignedLong remoteFinalizedBlockSlot =
      remoteFinalizedEpochSlot.minus(UnsignedLong.ONE);
  private final UnsignedLong earlierBlockSlot = earlierEpochSlot.minus(UnsignedLong.ONE);
  private final UnsignedLong laterBlockSlot = laterEpochSlot.minus(UnsignedLong.ONE);

  private final BeaconBlock remoteFinalizedBlock =
      DataStructureUtil.randomBeaconBlock(remoteFinalizedBlockSlot.longValue(), 1);
  private final BeaconBlock earlierBlock =
      DataStructureUtil.randomBeaconBlock(earlierBlockSlot.longValue(), 2);
  private final BeaconBlock laterBlock =
      DataStructureUtil.randomBeaconBlock(laterBlockSlot.longValue(), 3);

  private final Checkpoint remoteFinalizedCheckpoint =
      new Checkpoint(remoteFinalizedEpoch, remoteFinalizedBlock.signing_root("signature"));
  private final Checkpoint earlierCheckpoint =
      new Checkpoint(earlierEpoch, earlierBlock.signing_root("signature"));
  private final Checkpoint laterCheckpoint =
      new Checkpoint(laterEpoch, laterBlock.signing_root("signature"));

  private final StatusData remoteStatus = createStatusData();

  private final PeerChainValidator peerChainValidator =
      PeerChainValidator.create(storageClient, historicalChainData, peer);

  @BeforeEach
  public void setup() {
    when(storageClient.getStore()).thenReturn(store);
    when(peer.hasStatus()).thenReturn(true);
    when(peer.getStatus()).thenReturn(remoteStatus);
  }

  @Test
  public void chainsAreCompatible_finalizedCheckpointsMatch() {
    // Setup mocks
    forksMatch();
    finalizedCheckpointsMatch();

    peerChainValidator.run();
    assertPeerChainVerified();
  }

  @Test
  public void chainsAreCompatible_remoteChainIsAhead() {
    // Setup mocks
    forksMatch();
    remoteChainIsAheadOnSameChain();

    peerChainValidator.run();
    assertPeerChainVerified();
  }

  @Test
  public void chainsAreCompatible_remoteChainIsBehind() {
    // Setup mocks
    forksMatch();
    remoteChainIsBehindOnSameChain();

    peerChainValidator.run();
    assertPeerChainVerified();
  }

  @Test
  public void chainsAreIncompatible_remoteChainIsBehindOnDifferentChain() {
    // Setup mocks
    forksMatch();
    remoteChainIsBehindOnDifferentChain();

    peerChainValidator.run();
    assertPeerChainRejected(GoodbyeMessage.REASON_IRRELEVANT_NETWORK);
  }

  @Test
  public void chainsAreIncompatible_remoteChainIsAheadOnDifferentChain() {
    // Setup mocks
    forksMatch();
    remoteChainIsAheadOnDifferentChain();

    peerChainValidator.run();
    assertPeerChainRejected(GoodbyeMessage.REASON_IRRELEVANT_NETWORK);
  }

  @Test
  public void chainsAreIncompatible_remoteChainIsAhead_peerUnresponsive() {
    // Setup mocks
    forksMatch();
    remoteChainIsAheadAndUnresponsive();

    peerChainValidator.run();
    assertPeerChainRejected(GoodbyeMessage.REASON_UNABLE_TO_VERIFY_NETWORK);
  }

  @Test
  public void chainsAreCompatible_sameFork_preGenesis() {
    // Setup mocks
    forksMatch();
    // Store is null pre-genesis
    when(storageClient.isPreGenesis()).thenReturn(true);
    when(storageClient.getStore()).thenReturn(null);

    peerChainValidator.run();
    assertPeerChainVerified();
    // Verify remaining checks were skipped
    verify(peer, never()).requestBlockBySlot(any(), any());
    verify(historicalChainData, never()).getBlockBySlot(any());
    verify(store, never()).getFinalizedCheckpoint();
  }

  @Test
  public void chainsAreIncompatible_differentForks_finalizedCheckpointsMatch() {
    // Setup mocks
    forksDontMatch();

    peerChainValidator.run();
    assertPeerChainRejected(GoodbyeMessage.REASON_IRRELEVANT_NETWORK);
    // Verify other checks were skipped when fork mismatch was detected
    verify(peer, never()).requestBlockBySlot(any(), any());
    verify(historicalChainData, never()).getBlockBySlot(any());
    verify(store, never()).getFinalizedCheckpoint();
  }

  private void assertPeerChainRejected(UnsignedLong goodbyeReason) {
    verify(peer, never()).markChainValidated();
    verify(peer).sendGoodbye(goodbyeReason);
  }

  private void assertPeerChainVerified() {
    verify(peer).markChainValidated();
    verify(peer, never()).sendGoodbye(any());
  }

  private void forksMatch() {
    when(storageClient.getForkAtSlot(remoteStatus.getHeadSlot())).thenReturn(remoteFork);
  }

  private void forksDontMatch() {
    when(storageClient.getForkAtSlot(remoteStatus.getHeadSlot())).thenReturn(otherFork);
  }

  private void finalizedCheckpointsMatch() {
    final Checkpoint remoteFinalizedCheckpoint = getFinalizedCheckpoint(remoteStatus);
    when(store.getFinalizedCheckpoint()).thenReturn(remoteFinalizedCheckpoint);
  }

  private void remoteChainIsAheadOnSameChain() {
    final CompletableFuture<BeaconBlock> blockFuture =
        CompletableFuture.completedFuture(earlierBlock);
    final CompletableFuture<Optional<BeaconBlock>> optionalBlockFuture =
        CompletableFuture.completedFuture(Optional.of(earlierBlock));

    when(store.getFinalizedCheckpoint()).thenReturn(earlierCheckpoint);
    when(historicalChainData.getBlockBySlot(earlierEpochSlot)).thenReturn(optionalBlockFuture);
    when(peer.requestBlockBySlot(remoteStatus.getHeadRoot(), earlierBlockSlot))
        .thenReturn(blockFuture);
  }

  private void remoteChainIsAheadOnDifferentChain() {
    final CompletableFuture<BeaconBlock> blockFuture =
        CompletableFuture.completedFuture(randomBlock(earlierBlockSlot));
    final CompletableFuture<Optional<BeaconBlock>> optionalBlockFuture =
        CompletableFuture.completedFuture(Optional.of(earlierBlock));

    when(store.getFinalizedCheckpoint()).thenReturn(earlierCheckpoint);
    when(historicalChainData.getBlockBySlot(earlierEpochSlot)).thenReturn(optionalBlockFuture);
    when(peer.requestBlockBySlot(remoteStatus.getHeadRoot(), earlierBlockSlot))
        .thenReturn(blockFuture);
  }

  private void remoteChainIsAheadAndUnresponsive() {
    final CompletableFuture<BeaconBlock> blockFuture =
        CompletableFuture.failedFuture(new NullPointerException());
    final CompletableFuture<Optional<BeaconBlock>> optionalBlockFuture =
        CompletableFuture.completedFuture(Optional.of(earlierBlock));

    when(store.getFinalizedCheckpoint()).thenReturn(earlierCheckpoint);
    when(historicalChainData.getBlockBySlot(earlierEpochSlot)).thenReturn(optionalBlockFuture);
    when(peer.requestBlockBySlot(remoteStatus.getHeadRoot(), earlierBlockSlot))
        .thenReturn(blockFuture);
  }

  private void remoteChainIsBehindOnSameChain() {
    CompletableFuture<Optional<BeaconBlock>> blockResult =
        CompletableFuture.completedFuture(Optional.of(remoteFinalizedBlock));

    when(store.getFinalizedCheckpoint()).thenReturn(laterCheckpoint);
    when(historicalChainData.getBlockBySlot(remoteFinalizedEpochSlot)).thenReturn(blockResult);
  }

  private void remoteChainIsBehindOnDifferentChain() {
    CompletableFuture<Optional<BeaconBlock>> blockResult =
        CompletableFuture.completedFuture(Optional.of(randomBlock(remoteFinalizedBlockSlot)));

    when(store.getFinalizedCheckpoint()).thenReturn(laterCheckpoint);
    when(historicalChainData.getBlockBySlot(remoteFinalizedEpochSlot)).thenReturn(blockResult);
  }

  private BeaconBlock randomBlock(UnsignedLong slot) {
    return DataStructureUtil.randomBeaconBlock(slot.longValue(), slot.intValue());
  }

  private Checkpoint getFinalizedCheckpoint(final StatusData status) {
    return new Checkpoint(status.getFinalizedEpoch(), status.getFinalizedRoot());
  }

  private StatusData createStatusData() {

    final Bytes32 headRoot = Bytes32.fromHexString("0xeeee");
    // Set a head slot some distance beyond the finalized epoch
    final UnsignedLong headSlot =
        remoteFinalizedCheckpoint
            .getEpoch()
            .times(UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH))
            .plus(UnsignedLong.valueOf(10L));

    return new StatusData(
        remoteFork,
        remoteFinalizedCheckpoint.getRoot(),
        remoteFinalizedCheckpoint.getEpoch(),
        headRoot,
        headSlot);
  }
}
