/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil.BlockTimeliness;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoiceUtilGloasTest {

  private static final UInt64 GLOAS_FORK_EPOCH = UInt64.ONE;

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ForkChoiceUtilGloas forkChoiceUtil;
  private UInt64 gloasSlot;
  private BeaconState justifiedState;

  @BeforeEach
  void setUp() {
    spec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_FORK_EPOCH);
    dataStructureUtil = new DataStructureUtil(spec);
    gloasSlot = spec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH);
    forkChoiceUtil = ForkChoiceUtilGloas.required(spec.atSlot(gloasSlot).getForkChoiceUtil());
    justifiedState = dataStructureUtil.randomBeaconState(gloasSlot);
  }

  @Test
  void
      computeBlockTimeliness_shouldMarkBlockTimelyForAttestationsAndPtcBeforeAttestationDeadline() {
    final int millisIntoSlot = forkChoiceUtil.getAttestationDueMillis() - 1;

    assertThat(forkChoiceUtil.computeBlockTimeliness(gloasSlot, gloasSlot, millisIntoSlot))
        .isEqualTo(new BlockTimeliness(true, true));
  }

  @Test
  void computeBlockTimeliness_shouldKeepPtcTimelyAfterAttestationDeadlineButBeforePtcDeadline() {
    final int attestationDueMillis = forkChoiceUtil.getAttestationDueMillis();
    final int ptcDueMillis = forkChoiceUtil.getPayloadAttestationDueMillis().orElseThrow();

    assertThat(attestationDueMillis).isLessThan(ptcDueMillis);

    assertThat(forkChoiceUtil.computeBlockTimeliness(gloasSlot, gloasSlot, attestationDueMillis))
        .isEqualTo(new BlockTimeliness(false, true));
  }

  @Test
  void computeBlockTimeliness_shouldMarkBlockLateForAttestationsAndPtcAtPtcDeadline() {
    final int ptcDueMillis = forkChoiceUtil.getPayloadAttestationDueMillis().orElseThrow();

    assertThat(forkChoiceUtil.computeBlockTimeliness(gloasSlot, gloasSlot, ptcDueMillis))
        .isEqualTo(new BlockTimeliness(false, false));
  }

  @Test
  void
      computeBlockTimeliness_shouldMarkBlockLateForAttestationsAndPtcWhenBlockIsNotFromCurrentSlot() {
    assertThat(forkChoiceUtil.computeBlockTimeliness(gloasSlot, gloasSlot.plus(1), 0))
        .isEqualTo(new BlockTimeliness(false, false));
  }

  @Test
  void getParentPayloadStatus_shouldReturnFull_whenParentBlockHashMatchesMessageBlockHash() {
    // Create parent block with a specific block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references the parent's block hash
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), parentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<ForkChoicePayloadStatus> result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock);

    assertThat(result).isCompletedWithValue(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
  }

  @Test
  void getParentPayloadStatus_shouldReturnEmpty_whenParentBlockIsPreGloas() {
    // Create parent block which is pre-Gloas
    final UInt64 preGloasSlot = spec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH).minusMinZero(1);
    final BeaconBlock parentBlock = dataStructureUtil.randomBeaconBlock(preGloasSlot);

    // Create current block that references any block hash (doesn't matter since the check is not
    // done) but its parent is the pre-Gloas block
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(
            parentBlock.getRoot(), dataStructureUtil.randomBytes32());

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<ForkChoicePayloadStatus> result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock);

    assertThat(result).isCompletedWithValue(ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
  }

  @Test
  void getParentPayloadStatus_shouldReturnEmpty_whenParentBlockHashDoesNotMatch() {
    // Create parent block with one block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references a DIFFERENT parent block hash
    final Bytes32 differentParentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), differentParentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<ForkChoicePayloadStatus> result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock);

    assertThat(result).isCompletedWithValue(ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
  }

  @Test
  void getParentPayloadStatus_shouldThrowException_whenParentBlockNotFound() {
    final SignedBeaconBlock currentBlock = dataStructureUtil.randomSignedBeaconBlock();

    // Mock store with no parent block
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    // Test
    assertThat(forkChoiceUtil.getParentPayloadStatus(store, currentBlock.getMessage()))
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCauseInstanceOf(IllegalStateException.class)
        .withMessageContaining("Parent block not found");
  }

  @Test
  void isParentNodeFull_shouldReturnTrue_whenParentHasFullPayload() {
    // Create parent block with a specific block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references the parent's block hash
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), parentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<Boolean> result = forkChoiceUtil.isParentNodeFull(store, currentBlock);

    assertThat(result).isCompletedWithValue(true);
  }

  @Test
  void isParentNodeFull_shouldReturnFalse_whenParentHasEmptyPayload() {
    // Create parent block with one block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references a DIFFERENT parent block hash
    final Bytes32 differentParentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), differentParentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<Boolean> result = forkChoiceUtil.isParentNodeFull(store, currentBlock);

    assertThat(result).isCompletedWithValue(false);
  }

  @Test
  void isParentNodeFull_shouldThrowException_whenParentBlockNotFound() {
    final BeaconBlock currentBlock = dataStructureUtil.randomBeaconBlock();

    // Mock store with no parent block
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    // Test
    assertThat(forkChoiceUtil.isParentNodeFull(store, currentBlock))
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCauseInstanceOf(IllegalStateException.class)
        .withMessageContaining("Parent block not found");
  }

  @Test
  void isPayloadVerified_shouldReturnTrue_whenPayloadIsAvailable() {
    final SignedBeaconBlock currentBlock = dataStructureUtil.randomSignedBeaconBlock();
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getExecutionPayloadIfAvailable(currentBlock.getRoot()))
        .thenReturn(
            Optional.of(
                dataStructureUtil.randomSignedExecutionPayloadEnvelopeForBlock(currentBlock)));
    assertThat(forkChoiceUtil.isPayloadVerified(store, currentBlock.getRoot())).isTrue();
  }

  @Test
  void isPayloadVerified_shouldReturnFalse_whenPayloadIsNotAvailable() {
    final SignedBeaconBlock currentBlock = dataStructureUtil.randomSignedBeaconBlock();
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getExecutionPayloadIfAvailable(currentBlock.getRoot())).thenReturn(Optional.empty());
    assertThat(forkChoiceUtil.isPayloadVerified(store, currentBlock.getRoot())).isFalse();
  }

  @Test
  void getFullPayloadVoteHint_matchesAttestationIndex() {
    assertThat(forkChoiceUtil.getFullPayloadVoteHint(UInt64.ZERO)).isFalse();
    assertThat(forkChoiceUtil.getFullPayloadVoteHint(UInt64.ONE)).isTrue();
    assertThat(forkChoiceUtil.getFullPayloadVoteHint(UInt64.valueOf(2))).isFalse();
  }

  @Test
  void shouldApplyProposerBoost_returnsTrue_whenProposerBoostRootIsUnknown() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    when(strategy.blockParentRoot(boostRoot)).thenReturn(Optional.empty());
    assertThat(
            forkChoiceUtil.shouldApplyProposerBoost(
                boostRoot, strategy, UInt64.valueOf(100), justifiedState))
        .isTrue();
  }

  @Test
  void shouldApplyProposerBoost_returnsTrue_whenParentNotFromPreviousSlot() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    when(strategy.blockParentRoot(boostRoot)).thenReturn(Optional.of(parentRoot));
    when(strategy.blockSlot(boostRoot)).thenReturn(Optional.of(UInt64.valueOf(5)));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(UInt64.valueOf(3))); // gap > 1

    assertThat(
            forkChoiceUtil.shouldApplyProposerBoost(
                boostRoot, strategy, UInt64.valueOf(100), justifiedState))
        .isTrue();
  }

  @Test
  void shouldApplyProposerBoost_returnsTrue_whenParentIsNotWeak() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    when(strategy.blockParentRoot(boostRoot)).thenReturn(Optional.of(parentRoot));
    when(strategy.blockSlot(boostRoot)).thenReturn(Optional.of(gloasSlot.plus(1)));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(gloasSlot)); // consecutive
    // The weak-parent branch is currently deferred together with proposer equivocation handling.
    assertThat(
            forkChoiceUtil.shouldApplyProposerBoost(
                boostRoot, strategy, UInt64.ZERO, justifiedState))
        .isTrue();
  }

  @Test
  void shouldApplyProposerBoost_returnsTrue_whenParentIsWeakAndEquivocationBranchIsDeferred() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    when(strategy.blockParentRoot(boostRoot)).thenReturn(Optional.of(parentRoot));
    when(strategy.blockSlot(boostRoot)).thenReturn(Optional.of(gloasSlot.plus(1)));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(gloasSlot)); // consecutive
    // The equivocation suppression branch is intentionally not implemented yet.
    assertThat(
            forkChoiceUtil.shouldApplyProposerBoost(
                boostRoot, strategy, UInt64.valueOf(100), justifiedState))
        .isTrue();
  }

  @Test
  void isHeadWeak_shouldIgnoreAppliedProposerBoostWhenUsingProtoArrayWeight() {
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final UInt64 proposerBoostAmount = spec.getProposerBoostAmount(justifiedState);
    final BeaconState headState = dataStructureUtil.randomBeaconState(gloasSlot);
    final ProtoNodeData headNode = mock(ProtoNodeData.class);
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(headNode.getWeight()).thenReturn(proposerBoostAmount);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.of(justifiedState));
    when(store.getBlockStateIfAvailable(headRoot)).thenReturn(Optional.of(headState));
    when(store.getVote(ArgumentMatchers.any())).thenReturn(VoteTracker.DEFAULT);
    when(store.getProposerBoostRoot()).thenReturn(Optional.of(boostRoot));
    when(strategy.getBlockData(headRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING))
        .thenReturn(Optional.of(headNode));
    when(strategy.blockSlot(headRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(boostRoot, gloasSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createBase(headRoot)));

    assertThat(forkChoiceUtil.isHeadWeak(store, headRoot, UInt64.ONE)).isTrue();
  }

  @Test
  void isHeadWeak_shouldReturnFalseWhenRequiredStatesAreUnavailable() {
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.empty());
    when(store.getBlockStateIfAvailable(headRoot)).thenReturn(Optional.empty());

    assertThat(forkChoiceUtil.isHeadWeak(store, headRoot, UInt64.ONE)).isFalse();
  }

  @Test
  void isParentStrong_shouldIgnoreAppliedProposerBoostWhenEvaluatingResolvedParentNode() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(dataStructureUtil.randomBytes32());
    final Bytes32 parentRoot = parentBlock.getRoot();
    final BeaconBlock headBlock =
        createBlockWithParentAndParentBlockHash(
            parentRoot,
            BeaconBlockBodyGloas.required(parentBlock.getBody())
                .getSignedExecutionPayloadBid()
                .getMessage()
                .getBlockHash());
    final SignedBeaconBlock signedHead = mock(SignedBeaconBlock.class);
    final SignedBeaconBlock signedParent = mock(SignedBeaconBlock.class);
    final UInt64 proposerBoostAmount = spec.getProposerBoostAmount(justifiedState);
    final ProtoNodeData parentNode = mock(ProtoNodeData.class);
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(parentNode.getWeight()).thenReturn(proposerBoostAmount);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.of(justifiedState));
    when(store.getBlockIfAvailable(parentRoot)).thenReturn(Optional.of(signedParent));
    when(store.getProposerBoostRoot()).thenReturn(Optional.of(boostRoot));
    when(signedHead.getParentRoot()).thenReturn(parentRoot);
    when(signedHead.getMessage()).thenReturn(headBlock);
    when(signedParent.getMessage()).thenReturn(parentBlock);
    when(strategy.getBlockData(parentRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL))
        .thenReturn(Optional.of(parentNode));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(boostRoot, gloasSlot))
        .thenReturn(
            Optional.of(
                new ForkChoiceNode(parentRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL)));

    assertThat(forkChoiceUtil.isParentStrong(store, signedHead, UInt64.ZERO)).isFalse();
  }

  @Test
  void isParentStrong_shouldUseHeadPayloadLinkageWhenSelectingParentVariant() {
    final UInt64 parentThreshold = UInt64.ONE;
    final BeaconBlock parentBlock = createBlockWithBlockHash(dataStructureUtil.randomBytes32());
    final Bytes32 parentRoot = parentBlock.getRoot();
    final BeaconBlock headBlock =
        createBlockWithParentAndParentBlockHash(parentRoot, dataStructureUtil.randomBytes32());
    final SignedBeaconBlock signedHead = mock(SignedBeaconBlock.class);
    final SignedBeaconBlock signedParent = mock(SignedBeaconBlock.class);
    final ProtoNodeData emptyNode = mock(ProtoNodeData.class);
    final ProtoNodeData fullNode = mock(ProtoNodeData.class);
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(emptyNode.getWeight()).thenReturn(UInt64.ZERO);
    when(fullNode.getWeight()).thenReturn(parentThreshold.plus(1));
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.of(justifiedState));
    when(store.getBlockIfAvailable(parentRoot)).thenReturn(Optional.of(signedParent));
    when(store.getProposerBoostRoot()).thenReturn(Optional.empty());
    when(signedHead.getParentRoot()).thenReturn(parentRoot);
    when(signedHead.getMessage()).thenReturn(headBlock);
    when(signedParent.getMessage()).thenReturn(parentBlock);
    when(strategy.getBlockData(parentRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY))
        .thenReturn(Optional.of(emptyNode));
    when(strategy.getBlockData(parentRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL))
        .thenReturn(Optional.of(fullNode));

    assertThat(forkChoiceUtil.isParentStrong(store, signedHead, parentThreshold)).isFalse();
  }

  @Test
  void isParentStrong_shouldReturnFalseWhenImmediateInputsAreUnavailable() {
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock signedHead = mock(SignedBeaconBlock.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.empty());
    when(store.getBlockIfAvailable(parentRoot)).thenReturn(Optional.empty());
    when(signedHead.getParentRoot()).thenReturn(parentRoot);
    when(signedHead.getMessage()).thenReturn(dataStructureUtil.randomBeaconBlock(gloasSlot));

    assertThat(forkChoiceUtil.isParentStrong(store, signedHead, UInt64.ONE)).isFalse();
  }

  @Test
  void isParentStrong_shouldNotSubtractProposerBoostFromResolvedNodeWeight() {
    final BeaconBlock parentBlock = createBlockWithBlockHash(dataStructureUtil.randomBytes32());
    final Bytes32 parentRoot = parentBlock.getRoot();
    final BeaconBlock headBlock =
        createBlockWithParentAndParentBlockHash(parentRoot, dataStructureUtil.randomBytes32());
    final SignedBeaconBlock signedHead = mock(SignedBeaconBlock.class);
    final SignedBeaconBlock signedParent = mock(SignedBeaconBlock.class);
    final UInt64 proposerBoostAmount = spec.getProposerBoostAmount(justifiedState);
    final UInt64 attestationWeight = proposerBoostAmount.plus(1);
    final ProtoNodeData parentNode = mock(ProtoNodeData.class);
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(parentNode.getWeight()).thenReturn(attestationWeight);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.of(justifiedState));
    when(store.getBlockIfAvailable(parentRoot)).thenReturn(Optional.of(signedParent));
    when(store.getProposerBoostRoot()).thenReturn(Optional.of(parentRoot));
    when(signedHead.getParentRoot()).thenReturn(parentRoot);
    when(signedHead.getMessage()).thenReturn(headBlock);
    when(signedParent.getMessage()).thenReturn(parentBlock);
    when(strategy.getBlockData(parentRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY))
        .thenReturn(Optional.of(parentNode));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(parentRoot, gloasSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createBase(parentRoot)));

    assertThat(forkChoiceUtil.isParentStrong(store, signedHead, proposerBoostAmount)).isTrue();
  }

  // Helper methods to create blocks with specific properties
  private BeaconBlock createBlockWithBlockHash(final Bytes32 blockHash) {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(gloasSlot);
    final BeaconBlockBodyGloas body = BeaconBlockBodyGloas.required(block.getBody());
    final SignedExecutionPayloadBid signedBid = body.getSignedExecutionPayloadBid();
    final ExecutionPayloadBid bid = signedBid.getMessage();

    // Create a new bid with the desired block hash
    final ExecutionPayloadBid newBid =
        bid.getSchema()
            .create(
                bid.getParentBlockHash(),
                bid.getParentBlockRoot(),
                blockHash, // Set the desired block hash
                bid.getPrevRandao(),
                bid.getFeeRecipient(),
                bid.getGasLimit(),
                bid.getBuilderIndex(),
                bid.getSlot(),
                bid.getValue(),
                bid.getExecutionPayment(),
                bid.getBlobKzgCommitments(),
                bid.getExecutionRequestsRoot());

    // Create a new signed bid with the new bid
    final SignedExecutionPayloadBid newSignedBid =
        signedBid.getSchema().create(newBid, signedBid.getSignature());

    // Create a new body with the new signed bid
    final BeaconBlockBody newBody =
        body.getSchema()
            .createBlockBody(
                builder -> {
                  builder
                      .randaoReveal(body.getRandaoReveal())
                      .eth1Data(body.getEth1Data())
                      .graffiti(body.getGraffiti())
                      .attestations(body.getAttestations())
                      .proposerSlashings(body.getProposerSlashings())
                      .attesterSlashings(body.getAttesterSlashings())
                      .deposits(body.getDeposits())
                      .voluntaryExits(body.getVoluntaryExits())
                      .syncAggregate(body.getSyncAggregate())
                      .blsToExecutionChanges(body.getBlsToExecutionChanges())
                      .signedExecutionPayloadBid(newSignedBid)
                      .payloadAttestations(body.getPayloadAttestations())
                      .parentExecutionRequests(body.getParentExecutionRequests());
                  return SafeFuture.COMPLETE;
                })
            .join();

    // Create a new block with the new body
    return block
        .getSchema()
        .create(
            block.getSlot(),
            block.getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            newBody);
  }

  private BeaconBlock createBlockWithParentAndParentBlockHash(
      final Bytes32 parentRoot, final Bytes32 parentBlockHash) {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(gloasSlot);
    final BeaconBlockBodyGloas body = BeaconBlockBodyGloas.required(block.getBody());
    final SignedExecutionPayloadBid signedBid = body.getSignedExecutionPayloadBid();
    final ExecutionPayloadBid bid = signedBid.getMessage();

    // Create a new bid with the desired parent block hash
    final ExecutionPayloadBid newBid =
        bid.getSchema()
            .create(
                parentBlockHash, // Set the desired parent block hash
                bid.getParentBlockRoot(),
                bid.getBlockHash(),
                bid.getPrevRandao(),
                bid.getFeeRecipient(),
                bid.getGasLimit(),
                bid.getBuilderIndex(),
                bid.getSlot(),
                bid.getValue(),
                bid.getExecutionPayment(),
                bid.getBlobKzgCommitments(),
                bid.getExecutionRequestsRoot());

    // Create a new signed bid with the new bid
    final SignedExecutionPayloadBid newSignedBid =
        signedBid.getSchema().create(newBid, signedBid.getSignature());

    // Create a new body with the new signed bid
    final BeaconBlockBody newBody =
        body.getSchema()
            .createBlockBody(
                builder -> {
                  builder
                      .randaoReveal(body.getRandaoReveal())
                      .eth1Data(body.getEth1Data())
                      .graffiti(body.getGraffiti())
                      .attestations(body.getAttestations())
                      .proposerSlashings(body.getProposerSlashings())
                      .attesterSlashings(body.getAttesterSlashings())
                      .deposits(body.getDeposits())
                      .voluntaryExits(body.getVoluntaryExits())
                      .syncAggregate(body.getSyncAggregate())
                      .blsToExecutionChanges(body.getBlsToExecutionChanges())
                      .signedExecutionPayloadBid(newSignedBid)
                      .payloadAttestations(body.getPayloadAttestations())
                      .parentExecutionRequests(body.getParentExecutionRequests());
                  return SafeFuture.COMPLETE;
                })
            .join();

    // Create a new block with the new body and parent root
    return block
        .getSchema()
        .create(
            block.getSlot(),
            block.getProposerIndex(),
            parentRoot, // Set the desired parent root
            block.getStateRoot(),
            newBody);
  }
}
