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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.type.Bytes4;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.config.Constants;

public class PeerChainValidatorTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final CombinedChainDataClient combinedChainData = mock(CombinedChainDataClient.class);
  private final UpdatableStore store = mock(UpdatableStore.class);

  private final ForkInfo remoteForkInfo = dataStructureUtil.randomForkInfo();
  private final Bytes4 remoteFork = remoteForkInfo.getForkDigest();
  private final ForkInfo otherForkInfo = dataStructureUtil.randomForkInfo();

  private final UInt64 genesisEpoch = UInt64.valueOf(Constants.GENESIS_EPOCH);
  private final UInt64 remoteFinalizedEpoch = UInt64.valueOf(10L);
  private final UInt64 earlierEpoch = UInt64.valueOf(8L);
  private final UInt64 laterEpoch = UInt64.valueOf(12L);

  private final UInt64 genesisSlot = compute_start_slot_at_epoch(genesisEpoch);
  private final UInt64 remoteFinalizedEpochSlot = compute_start_slot_at_epoch(remoteFinalizedEpoch);
  private final UInt64 earlierEpochSlot = compute_start_slot_at_epoch(earlierEpoch);
  private final UInt64 laterEpochSlot = compute_start_slot_at_epoch(laterEpoch);

  // Offset slots from epoch to simulate skipped blocks
  private final UInt64 remoteFinalizedBlockSlot = remoteFinalizedEpochSlot.minus(UInt64.ONE);
  private final UInt64 earlierBlockSlot = earlierEpochSlot.minus(UInt64.ONE);
  private final UInt64 laterBlockSlot = laterEpochSlot.minus(UInt64.ONE);

  private final SignedBlockAndState genesisBlockAndState =
      dataStructureUtil.randomSignedBlockAndState(genesisSlot);
  private final SignedBlockAndState remoteFinalizedBlockAndState =
      dataStructureUtil.randomSignedBlockAndState(remoteFinalizedBlockSlot);
  private final SignedBlockAndState earlierBlockAndState =
      dataStructureUtil.randomSignedBlockAndState(earlierBlockSlot);
  private final SignedBlockAndState laterBlockAndState =
      dataStructureUtil.randomSignedBlockAndState(laterBlockSlot);

  private final Checkpoint genesisCheckpoint =
      new Checkpoint(genesisEpoch, genesisBlockAndState.getRoot());
  private final Checkpoint remoteFinalizedCheckpoint =
      new Checkpoint(remoteFinalizedEpoch, remoteFinalizedBlockAndState.getRoot());
  private final Checkpoint earlierCheckpoint =
      new Checkpoint(earlierEpoch, earlierBlockAndState.getRoot());
  private final Checkpoint laterCheckpoint =
      new Checkpoint(laterEpoch, laterBlockAndState.getRoot());

  private final AnchorPoint genesisAnchor =
      AnchorPoint.create(genesisCheckpoint, genesisBlockAndState);
  private final AnchorPoint remoteFinalizedAnchor =
      AnchorPoint.create(remoteFinalizedCheckpoint, remoteFinalizedBlockAndState);
  private final AnchorPoint earlierAnchor =
      AnchorPoint.create(earlierCheckpoint, earlierBlockAndState);
  private final AnchorPoint laterAnchor = AnchorPoint.create(laterCheckpoint, laterBlockAndState);

  private PeerStatus remoteStatus;
  private PeerChainValidator peerChainValidator;

  @BeforeEach
  public void setup() {
    setupRemoteStatusAndValidator(remoteFinalizedCheckpoint);
    when(peer.getId()).thenReturn(new MockNodeId());
    when(peer.hasStatus()).thenReturn(true);
    when(peer.disconnectCleanly(any())).thenReturn(SafeFuture.completedFuture(null));

    when(combinedChainData.getCurrentEpoch()).thenReturn(compute_epoch_at_slot(laterBlockSlot));
    when(combinedChainData.getStore()).thenReturn(store);
  }

  @Test
  public void chainsAreCompatible_finalizedCheckpointsMatch() {
    // Setup mocks
    forksMatch();
    finalizedCheckpointsMatch();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  @Test
  public void chainsAreCompatible_finalizedCheckpointsMatch_latestFinalizedBlockMissing() {
    // Setup mocks
    forksMatch();
    finalizedCheckpointsMatch(false);

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  @Test
  public void chainsAreCompatible_remoteChainIsAhead() {
    // Setup mocks
    forksMatch();
    remoteChainIsAheadOnSameChain();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  @Test
  public void chainsAreCompatible_remoteChainIsAhead_latestFinalizedBlockMissing() {
    // Setup mocks
    forksMatch();
    remoteChainIsAheadOnSameChain(false);

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  // Prysm nodes will not send the genesis block, so make sure we handle this case
  @Test
  public void chainsAreCompatible_localChainAtGenesisRemote_remoteWillNotReturnGenesis() {
    // Setup mocks
    forksMatch();
    remoteOnSameChainButReturnsInvalidResponseWhenGenesisRequested();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  @Test
  public void chainsAreCompatible_remoteChainIsBehind() {
    // Setup mocks
    forksMatch();
    remoteChainIsBehindOnSameChain();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  @Test
  public void chainsAreCompatible_remoteChainIsBehind_latestFinalizedBlockMissing() {
    // Setup mocks
    forksMatch();
    remoteChainIsBehindOnSameChain(false);

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  @Test
  public void chainsAreCompatible_remoteFinalizedCheckpointIsFromGenesisEpoch() {
    // Setup mocks
    setupRemoteStatusAndValidator(genesisCheckpoint);
    forksMatch();
    remoteCheckpointIsAtCurrentEpoch();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  @Test
  public void chainsAreInCompatible_remoteFinalizedCheckpointIsFromCurrentEpoch() {
    // Setup mocks
    forksMatch();
    remoteCheckpointIsAtCurrentEpoch();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainRejected(result, DisconnectReason.IRRELEVANT_NETWORK);
  }

  @Test
  public void chainsAreInCompatible_remoteFinalizedCheckpointIsFromFutureEpoch() {
    // Setup mocks
    forksMatch();
    remoteCheckpointIsAtFutureEpoch();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainRejected(result, DisconnectReason.IRRELEVANT_NETWORK);
  }

  @Test
  public void chainsAreIncompatible_remoteChainIsBehindOnDifferentChain() {
    // Setup mocks
    forksMatch();
    remoteChainIsBehindOnDifferentChain();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainRejected(result, DisconnectReason.IRRELEVANT_NETWORK);
  }

  @Test
  public void chainsAreIncompatible_remoteChainIsAheadOnDifferentChain() {
    // Setup mocks
    forksMatch();
    remoteChainIsAheadOnDifferentChain();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainRejected(result, DisconnectReason.IRRELEVANT_NETWORK);
  }

  @Test
  public void chainsAreIncompatible_remoteChainIsAhead_peerUnresponsive() {
    // Setup mocks
    forksMatch();
    remoteChainIsAheadAndUnresponsive();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainRejected(result, DisconnectReason.UNABLE_TO_VERIFY_NETWORK);
  }

  @Test
  public void chainsAreIncompatible_differentForks() {
    // Setup mocks
    forksDontMatch();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainRejected(result, DisconnectReason.IRRELEVANT_NETWORK);
    // Verify other checks were skipped when fork mismatch was detected
    verify(peer, never()).requestBlockBySlot(any());
    verify(combinedChainData, never()).getBlockInEffectAtSlot(any());
    verify(store, never()).getFinalizedCheckpoint();
  }

  @Test
  public void validateRequiredCheckpoint_peerFinalizedBlockMatchesRequiredCheckpoint() {
    setupRemoteStatusAndValidator(
        remoteFinalizedCheckpoint, Optional.of(remoteFinalizedCheckpoint));
    // Setup mocks
    forksMatch();
    finalizedCheckpointsMatch();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  @Test
  public void validateRequiredCheckpoint_peerFinalizedBlockDoesNotMatchRequiredCheckpoint() {
    final Checkpoint requiredCheckpoint =
        new Checkpoint(remoteFinalizedEpoch, dataStructureUtil.randomBytes32());
    setupRemoteStatusAndValidator(remoteFinalizedCheckpoint, Optional.of(requiredCheckpoint));
    // Setup mocks
    forksMatch();
    finalizedCheckpointsMatch();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainRejected(result, DisconnectReason.IRRELEVANT_NETWORK);
  }

  @Test
  public void validateRequiredCheckpoint_peerHistoricalBlockMatchesRequiredCheckpoint() {
    final Checkpoint requiredCheckpoint = earlierCheckpoint;
    setupRemoteStatusAndValidator(remoteFinalizedCheckpoint, Optional.of(earlierCheckpoint));
    // Setup mocks
    forksMatch();
    finalizedCheckpointsMatch();
    when(peer.requestBlockByRoot(requiredCheckpoint.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(earlierBlockAndState.getBlock())));
    when(peer.requestBlockBySlot(earlierBlockAndState.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(earlierBlockAndState.getBlock())));

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  @Test
  public void validateRequiredCheckpoint_peerHistoricalBlockDoesNotMatchRequiredCheckpoint() {
    final Checkpoint requiredCheckpoint =
        new Checkpoint(earlierEpoch, dataStructureUtil.randomBytes32());
    setupRemoteStatusAndValidator(remoteFinalizedCheckpoint, Optional.of(requiredCheckpoint));
    // Setup mocks
    forksMatch();
    finalizedCheckpointsMatch();
    final SignedBeaconBlock nonMatchingBlock =
        dataStructureUtil.randomSignedBeaconBlock(earlierCheckpoint.getEpochStartSlot());
    when(peer.requestBlockByRoot(requiredCheckpoint.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(earlierBlockAndState.getBlock())));
    when(peer.requestBlockBySlot(earlierBlockAndState.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(nonMatchingBlock)));

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainRejected(result, DisconnectReason.IRRELEVANT_NETWORK);
  }

  @Test
  public void validateRequiredCheckpoint_noResultReturned() {
    final Checkpoint requiredCheckpoint =
        new Checkpoint(earlierEpoch, dataStructureUtil.randomBytes32());
    setupRemoteStatusAndValidator(remoteFinalizedCheckpoint, Optional.of(requiredCheckpoint));
    // Setup mocks
    forksMatch();
    finalizedCheckpointsMatch();
    when(peer.requestBlockByRoot(requiredCheckpoint.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainRejected(result, DisconnectReason.IRRELEVANT_NETWORK);
  }

  @Test
  public void validateRequiredCheckpoint_requiredCheckpointNotFinal() {
    setupRemoteStatusAndValidator(remoteFinalizedCheckpoint, Optional.of(laterCheckpoint));
    // Setup mocks
    forksMatch();
    finalizedCheckpointsMatch();

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainVerified(result);
  }

  @Test
  public void remoteChainIsBehindLocalAnchorPoint_disallow() {
    peerChainValidator =
        PeerChainValidator.create(new NoOpMetricsSystem(), combinedChainData, Optional.empty());

    // Setup mocks
    forksMatch();
    remoteChainIsBehindOnSameChain();
    when(combinedChainData.getBlockInEffectAtSlot(remoteFinalizedEpochSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<Boolean> result = peerChainValidator.validate(peer, remoteStatus);
    assertPeerChainRejected(result, DisconnectReason.IRRELEVANT_NETWORK);
  }

  private void assertPeerChainRejected(
      final SafeFuture<Boolean> result, DisconnectReason goodbyeReason) {
    assertThat(result).isCompletedWithValue(false);
    verify(peer).disconnectCleanly(goodbyeReason);
  }

  private void assertPeerChainVerified(final SafeFuture<Boolean> result) {
    assertThat(result).isCompletedWithValue(true);
    verify(peer, never()).sendGoodbye(any());
    verify(peer, never()).disconnectCleanly(any());
  }

  private void forksMatch() {
    when(combinedChainData.getHeadForkInfo()).thenReturn(Optional.of(remoteForkInfo));
  }

  private void forksDontMatch() {
    when(combinedChainData.getHeadForkInfo()).thenReturn(Optional.of(otherForkInfo));
  }

  private void finalizedCheckpointsMatch() {
    finalizedCheckpointsMatch(true);
  }

  private void finalizedCheckpointsMatch(final boolean isLocalFinalizedBlockAvailable) {
    withLocalFinalizedAnchor(remoteFinalizedAnchor, isLocalFinalizedBlockAvailable);
  }

  private void remoteCheckpointIsAtCurrentEpoch() {
    final Checkpoint remoteFinalizedCheckpoint = getFinalizedCheckpoint(remoteStatus);
    withLocalFinalizedAnchor(genesisAnchor);
    when(combinedChainData.getCurrentEpoch()).thenReturn(remoteFinalizedCheckpoint.getEpoch());
  }

  private void remoteCheckpointIsAtFutureEpoch() {
    final Checkpoint remoteFinalizedCheckpoint = getFinalizedCheckpoint(remoteStatus);
    withLocalFinalizedAnchor(genesisAnchor);

    when(combinedChainData.getCurrentEpoch())
        .thenReturn(remoteFinalizedCheckpoint.getEpoch().minus(1));
  }

  private void remoteChainIsAheadOnSameChain() {
    remoteChainIsAheadOnSameChain(true);
  }

  private void remoteChainIsAheadOnSameChain(final boolean isLocalFinalizedBlockAvailable) {
    final SafeFuture<Optional<SignedBeaconBlock>> blockFuture =
        SafeFuture.completedFuture(Optional.of(earlierBlockAndState.getBlock()));
    final SafeFuture<Optional<SignedBeaconBlock>> optionalBlockFuture =
        SafeFuture.completedFuture(Optional.of(earlierBlockAndState.getBlock()));

    withLocalFinalizedAnchor(earlierAnchor, isLocalFinalizedBlockAvailable);
    when(combinedChainData.getBlockInEffectAtSlot(earlierEpochSlot))
        .thenReturn(optionalBlockFuture);
    when(peer.requestBlockBySlot(earlierBlockSlot)).thenReturn(blockFuture);
  }

  private void remoteOnSameChainButReturnsInvalidResponseWhenGenesisRequested() {
    final SafeFuture<Optional<SignedBeaconBlock>> optionalBlockFuture =
        SafeFuture.completedFuture(Optional.of(genesisBlockAndState.getBlock()));

    withLocalFinalizedAnchor(genesisAnchor);
    when(combinedChainData.getBlockInEffectAtSlot(genesisSlot)).thenReturn(optionalBlockFuture);
    when(peer.requestBlockBySlot(genesisSlot))
        .thenReturn(
            SafeFuture.failedFuture(
                new IllegalStateException(
                    "Received multiple responses when single response expected")));
  }

  private void remoteChainIsAheadOnDifferentChain() {
    final SafeFuture<Optional<SignedBeaconBlock>> blockFuture =
        SafeFuture.completedFuture(Optional.of(randomBlock(earlierBlockSlot)));
    final SafeFuture<Optional<SignedBeaconBlock>> optionalBlockFuture =
        SafeFuture.completedFuture(Optional.of(earlierBlockAndState.getBlock()));

    withLocalFinalizedAnchor(earlierAnchor);
    when(combinedChainData.getBlockInEffectAtSlot(earlierEpochSlot))
        .thenReturn(optionalBlockFuture);
    when(peer.requestBlockBySlot(earlierBlockSlot)).thenReturn(blockFuture);
  }

  private void remoteChainIsAheadAndUnresponsive() {
    final SafeFuture<Optional<SignedBeaconBlock>> blockFuture =
        SafeFuture.failedFuture(new NullPointerException());
    final SafeFuture<Optional<SignedBeaconBlock>> optionalBlockFuture =
        SafeFuture.completedFuture(Optional.of(earlierBlockAndState.getBlock()));

    withLocalFinalizedAnchor(earlierAnchor);
    when(combinedChainData.getBlockInEffectAtSlot(earlierEpochSlot))
        .thenReturn(optionalBlockFuture);
    when(peer.requestBlockBySlot(earlierBlockSlot)).thenReturn(blockFuture);
  }

  private void remoteChainIsBehindOnSameChain() {
    remoteChainIsBehindOnSameChain(true);
  }

  private void remoteChainIsBehindOnSameChain(final boolean isLocalFinalizedBlockAvailable) {
    SafeFuture<Optional<SignedBeaconBlock>> blockResult =
        SafeFuture.completedFuture(Optional.of(remoteFinalizedBlockAndState.getBlock()));

    withLocalFinalizedAnchor(laterAnchor, isLocalFinalizedBlockAvailable);
    when(combinedChainData.getBlockInEffectAtSlot(remoteFinalizedEpochSlot))
        .thenReturn(blockResult);
  }

  private void remoteChainIsBehindOnDifferentChain() {
    SafeFuture<Optional<SignedBeaconBlock>> blockResult =
        SafeFuture.completedFuture(Optional.of(randomBlock(remoteFinalizedBlockSlot)));

    withLocalFinalizedAnchor(laterAnchor);
    when(combinedChainData.getBlockInEffectAtSlot(remoteFinalizedEpochSlot))
        .thenReturn(blockResult);
  }

  private SignedBeaconBlock randomBlock(UInt64 slot) {
    return dataStructureUtil.randomSignedBeaconBlock(slot.longValue());
  }

  private Checkpoint getFinalizedCheckpoint(final PeerStatus status) {
    return new Checkpoint(status.getFinalizedEpoch(), status.getFinalizedRoot());
  }

  private void withLocalFinalizedAnchor(final AnchorPoint finalizedAnchor) {
    withLocalFinalizedAnchor(finalizedAnchor, true);
  }

  private void withLocalFinalizedAnchor(
      final AnchorPoint finalizedAnchor, final boolean isLocalFinalizedBlockAvailable) {
    if (isLocalFinalizedBlockAvailable) {
      when(store.getLatestFinalized()).thenReturn(finalizedAnchor);
    } else {
      final AnchorPoint anchorMissingBlock =
          AnchorPoint.create(
              finalizedAnchor.getCheckpoint(), finalizedAnchor.getState(), Optional.empty());
      when(store.getLatestFinalized()).thenReturn(anchorMissingBlock);
    }
  }

  private void setupRemoteStatusAndValidator(final Checkpoint remoteFinalizedCheckpoint) {
    setupRemoteStatusAndValidator(remoteFinalizedCheckpoint, Optional.empty());
  }

  private void setupRemoteStatusAndValidator(
      final Checkpoint remoteFinalizedCheckpoint, final Optional<Checkpoint> requiredCheckpoint) {

    final Bytes32 headRoot = Bytes32.fromHexString("0xeeee");
    // Set a head slot some distance beyond the finalized epoch
    final UInt64 headSlot =
        remoteFinalizedCheckpoint.getEpoch().times(Constants.SLOTS_PER_EPOCH).plus(10L);

    final PeerStatus status =
        new PeerStatus(
            remoteFork,
            remoteFinalizedCheckpoint.getRoot(),
            remoteFinalizedCheckpoint.getEpoch(),
            headRoot,
            headSlot);
    when(peer.getStatus()).thenReturn(status);

    remoteStatus = status;
    peerChainValidator =
        PeerChainValidator.create(new NoOpMetricsSystem(), combinedChainData, requiredCheckpoint);
  }
}
