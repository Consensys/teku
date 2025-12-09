/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PROPOSER_LOOKAHEAD;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateSchemaFulu;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

@TestSpecContext(
    signatureVerifierNoop = true,
    milestone = {SpecMilestone.PHASE0, SpecMilestone.FULU, SpecMilestone.GLOAS})
public class GossipValidationHelperTest {
  private Spec spec;
  private RecentChainData recentChainData;
  private DataStructureUtil dataStructureUtil;
  private StorageSystem storageSystem;
  private GossipValidationHelper gossipValidationHelper;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis(false);
    recentChainData = storageSystem.recentChainData();

    gossipValidationHelper =
        new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem());
  }

  @TestTemplate
  void isSlotFinalized_shouldComputeCorrectly() {

    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    assertThat(gossipValidationHelper.isSlotFinalized(finalizedSlot)).isFalse();

    storageSystem.chainUpdater().advanceChain(finalizedSlot);
    storageSystem.chainUpdater().finalizeEpoch(finalizedEpoch);

    assertThat(gossipValidationHelper.isSlotFinalized(finalizedSlot)).isTrue();
    assertThat(gossipValidationHelper.isSlotFinalized(finalizedSlot.plus(1))).isFalse();
  }

  @TestTemplate
  void isSlotFromFuture_shouldComputeCorrectly() {
    final UInt64 slot2 = UInt64.valueOf(2);

    storageSystem.chainUpdater().setCurrentSlot(UInt64.ONE);
    assertThat(gossipValidationHelper.isSlotFromFuture(slot2)).isTrue();

    final UInt64 slot2TimeMillis =
        spec.computeTimeMillisAtSlot(
            slot2,
            secondsToMillis(
                recentChainData.getBestState().orElseThrow().getImmediately().getGenesisTime()));

    final UInt64 notYetInsideTolerance =
        slot2TimeMillis.minusMinZero(gossipValidationHelper.getMaxOffsetTimeInMillis()).decrement();
    storageSystem.chainUpdater().setTimeMillis(notYetInsideTolerance);
    assertThat(gossipValidationHelper.isSlotFromFuture(slot2)).isTrue();

    final UInt64 insideTolerance =
        slot2TimeMillis.minusMinZero(gossipValidationHelper.getMaxOffsetTimeInMillis());
    storageSystem.chainUpdater().setTimeMillis(insideTolerance);
    assertThat(gossipValidationHelper.isSlotFromFuture(slot2)).isFalse();
  }

  @TestTemplate
  void isSignatureValidWithRespectToProposerIndex_shouldComputeCorrectly() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock signedBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    final BeaconState headState =
        SafeFutureAssert.safeJoin(recentChainData.getBestState().orElseThrow());

    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_PROPOSER,
            spec.getCurrentEpoch(headState),
            headState.getFork(),
            headState.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(signedBlock.getMessage(), domain);

    assertThat(
            gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
                signingRoot, signedBlock.getProposerIndex(), signedBlock.getSignature(), headState))
        .isTrue();

    // wrong proposer index
    assertThat(
            gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
                signingRoot,
                signedBlock.getProposerIndex().increment(),
                signedBlock.getSignature(),
                headState))
        .isFalse();
  }

  @TestTemplate
  void isProposerTheExpectedProposer_shouldComputeCorrectly() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock signedBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    final BeaconState headState =
        SafeFutureAssert.safeJoin(recentChainData.getBestState().orElseThrow());

    assertThat(
            gossipValidationHelper.isProposerTheExpectedProposer(
                signedBlock.getProposerIndex(), nextSlot, headState))
        .isTrue();

    // wrong proposer index
    assertThat(
            gossipValidationHelper.isProposerTheExpectedProposer(
                signedBlock.getProposerIndex().increment(), nextSlot, headState))
        .isFalse();
  }

  @TestTemplate
  void isProposerTheExpectedProposer_GetsProposerFromStateInFulu() {
    assumeThat(spec.atEpoch(ZERO).getMilestone()).isGreaterThanOrEqualTo(SpecMilestone.FULU);
    final UInt64 defaultProposerIndex = UInt64.valueOf(1234);

    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final BeaconState headState =
        SafeFutureAssert.safeJoin(recentChainData.getBestState().orElseThrow());
    final BeaconStateSchemaFulu beaconStateSchema =
        (BeaconStateSchemaFulu)
            spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions().getBeaconStateSchema();
    final SszUInt64VectorSchema<?> proposerLookaheadVectorSchema =
        beaconStateSchema.getProposerLookaheadSchema();
    final SszMutableContainer writableCopy = headState.createWritableCopy();
    writableCopy.set(
        beaconStateSchema.getFieldIndex(PROPOSER_LOOKAHEAD),
        proposerLookaheadVectorSchema.createFromElements(
            IntStream.range(0, proposerLookaheadVectorSchema.getLength())
                .mapToObj(__ -> SszUInt64.of(defaultProposerIndex))
                .toList()));
    final BeaconState modifiedState = (BeaconState) writableCopy.commitChanges();

    assertThat(
            gossipValidationHelper.isProposerTheExpectedProposer(
                defaultProposerIndex, nextSlot, modifiedState))
        .isTrue();
    assertThat(
            gossipValidationHelper.isProposerTheExpectedProposer(
                defaultProposerIndex.increment(), nextSlot, modifiedState))
        .isFalse();
  }

  @TestTemplate
  void getSlotForBlockRoot_shouldComputeCorrectly() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState nextBlockAndState =
        storageSystem.chainUpdater().advanceChain(nextSlot);

    assertThat(gossipValidationHelper.getSlotForBlockRoot(nextBlockAndState.getRoot()))
        .containsSame(nextSlot);

    assertThat(gossipValidationHelper.getSlotForBlockRoot(dataStructureUtil.randomBytes32()))
        .isEmpty();
  }

  @TestTemplate
  void isBlockAvailable_shouldComputeCorrectly() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState nextBlockAndState =
        storageSystem.chainUpdater().advanceChain(nextSlot);

    assertThat(gossipValidationHelper.isBlockAvailable(nextBlockAndState.getRoot())).isTrue();

    assertThat(gossipValidationHelper.isBlockAvailable(dataStructureUtil.randomBytes32()))
        .isFalse();
  }

  @TestTemplate
  void getParentStateInBlockEpoch_shouldComputeCorrectly() {
    final UInt64 firstSlotAtEpoch1 = spec.computeStartSlotAtEpoch(ONE);

    final UInt64 lastSlotInEpoch0 = firstSlotAtEpoch1.minus(1);

    final SignedBlockAndState lastBlockStateInEpoch0 =
        storageSystem.chainUpdater().advanceChain(firstSlotAtEpoch1.minus(2));

    // should get parent state in same epoch
    assertThatSafeFuture(
            gossipValidationHelper.getParentStateInBlockEpoch(
                lastBlockStateInEpoch0.getSlot(),
                lastBlockStateInEpoch0.getRoot(),
                lastSlotInEpoch0))
        .isCompletedWithValueMatching(
            beaconState -> beaconState.orElseThrow().equals(lastBlockStateInEpoch0.getState()));

    // should generate a state for epoch 1
    assertThatSafeFuture(
            gossipValidationHelper.getParentStateInBlockEpoch(
                lastBlockStateInEpoch0.getSlot(),
                lastBlockStateInEpoch0.getRoot(),
                firstSlotAtEpoch1))
        .isCompletedWithValueMatching(
            beaconState -> beaconState.orElseThrow().getSlot().equals(firstSlotAtEpoch1));
  }

  @TestTemplate
  void currentFinalizedCheckpointIsAncestorOfBlock_shouldReturnValid() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    assertThat(
            gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
                block.getSlot(), block.getParentRoot()))
        .isTrue();
  }

  @TestTemplate
  void
      currentFinalizedCheckpointIsAncestorOfBlock_shouldReturnInvalidForBlockThatDoesNotDescendFromFinalizedCheckpoint() {
    List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(4);

    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    final RecentChainData localRecentChainData = storageSystem.recentChainData();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec, validatorKeys);
    final ChainUpdater chainUpdater = new ChainUpdater(localRecentChainData, chainBuilder, spec);

    final GossipValidationHelper gossipValidationHelper =
        new GossipValidationHelper(spec, localRecentChainData, storageSystem.getMetricsSystem());
    chainUpdater.initializeGenesis();

    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    final ChainBuilder chainBuilderFork = chainBuilder.fork();
    final ChainUpdater chainUpdaterFork =
        new ChainUpdater(storageSystem.recentChainData(), chainBuilderFork, spec);

    final UInt64 startSlotOfFinalizedEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(4));

    chainUpdaterFork.advanceChain(20);

    chainUpdater.finalizeEpoch(4);

    SignedBlockAndState blockAndState =
        chainBuilderFork.generateBlockAtSlot(startSlotOfFinalizedEpoch.increment());
    chainUpdater.saveBlockTime(blockAndState);

    assertThat(
            gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
                blockAndState.getSlot(), blockAndState.getParentRoot()))
        .isFalse();
  }

  @TestTemplate
  void isSlotCurrent_shouldRejectOutsideLowerBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 slotStartTimeMillis = getSlotStartTimeMillis(slot);
    final UInt64 currentTime =
        slotStartTimeMillis.minus(gossipValidationHelper.getMaxOffsetTimeInMillis()).decrement();
    assertIsSlotCurrent(slot, currentTime, false);
  }

  @TestTemplate
  void isSlotCurrent_shouldAcceptLowerBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 slotStartTimeMillis = getSlotStartTimeMillis(slot);
    final UInt64 currentTime =
        slotStartTimeMillis.minus(gossipValidationHelper.getMaxOffsetTimeInMillis());
    assertIsSlotCurrent(slot, currentTime, true);
  }

  @TestTemplate
  void isSlotCurrent_shouldAcceptUpperBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 nextSlotStartTimeMillis = getSlotStartTimeMillis(slot.increment());
    final UInt64 currentTime =
        nextSlotStartTimeMillis.plus(gossipValidationHelper.getMaxOffsetTimeInMillis());
    assertIsSlotCurrent(slot, currentTime, true);
  }

  @TestTemplate
  void isSlotCurrent_shouldRejectOutsideUpperBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 nextSlotStartTimeMillis = getSlotStartTimeMillis(slot.increment());
    final UInt64 currentTime =
        nextSlotStartTimeMillis.plus(gossipValidationHelper.getMaxOffsetTimeInMillis()).increment();
    assertIsSlotCurrent(slot, currentTime, false);
  }

  @TestTemplate
  void isCurrentOrNextSlot() {
    final UInt64 currentSlot = UInt64.valueOf(10);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);
    assertThat(gossipValidationHelper.isSlotCurrentOrNext(currentSlot)).isTrue();
    assertThat(gossipValidationHelper.isSlotCurrentOrNext(currentSlot.plus(ONE))).isTrue();
    assertThat(gossipValidationHelper.isSlotCurrentOrNext(currentSlot.minus(ONE))).isFalse();
    assertThat(gossipValidationHelper.isSlotCurrentOrNext(currentSlot.plus(2))).isFalse();
  }

  @TestTemplate
  void isValidBuilderIndex_shouldReturnTrueForValidActiveNotSlashedValidator(
      final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BeaconState state = recentChainData.getBestState().orElseThrow().getImmediately();
    final UInt64 slot = state.getSlot();
    final UInt64 validIndex = UInt64.valueOf(0);
    assertThat(gossipValidationHelper.isValidBuilderIndex(validIndex, state, slot)).isTrue();
  }

  @TestTemplate
  void isValidBuilderIndex_shouldHandleOutOfBoundIndex(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BeaconState state = recentChainData.getBestState().orElseThrow().getImmediately();
    final UInt64 slot = state.getSlot();
    final UInt64 invalidIndex = UInt64.valueOf(state.getValidators().size());
    assertThat(gossipValidationHelper.isValidBuilderIndex(invalidIndex, state, slot)).isFalse();
  }

  @TestTemplate
  void isValidBuilderIndex_shouldReturnFalseForSlashedValidator(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BeaconState originalState = recentChainData.getBestState().orElseThrow().getImmediately();
    final UInt64 slot = originalState.getSlot();
    final Validator slashedValidator =
        dataStructureUtil
            .validatorBuilder()
            .slashed(true)
            .activationEpoch(spec.computeEpochAtSlot(slot))
            .build();
    final BeaconState modifiedState =
        originalState.updated(
            mutableState -> {
              final SszMutableList<Validator> validators = mutableState.getValidators();
              validators.append(slashedValidator);
              mutableState.setValidators(validators);
              mutableState.setSlot(slot);
            });
    final UInt64 slashedValidatorIndex =
        UInt64.valueOf(modifiedState.getValidators().size()).decrement();
    assertThat(
            gossipValidationHelper.isValidBuilderIndex(slashedValidatorIndex, modifiedState, slot))
        .isFalse();
  }

  @TestTemplate
  void isValidBuilderIndex_shouldReturnFalseForInactiveValidator(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BeaconState originalState = recentChainData.getBestState().orElseThrow().getImmediately();
    final UInt64 slot = originalState.getSlot();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);

    final Validator incativeValidator =
        dataStructureUtil.validatorBuilder().exitEpoch(currentEpoch).build();
    final BeaconState modifiedState =
        originalState.updated(
            mutableState -> {
              final SszMutableList<Validator> validators = mutableState.getValidators();
              validators.append(incativeValidator);
              mutableState.setValidators(validators);
              mutableState.setSlot(slot);
            });
    final UInt64 inactiveIndex = UInt64.valueOf(modifiedState.getValidators().size()).decrement();
    assertThat(gossipValidationHelper.isValidBuilderIndex(inactiveIndex, modifiedState, slot))
        .isFalse();
  }

  private UInt64 getSlotStartTimeMillis(final UInt64 slot) {
    return spec.computeTimeAtSlot(slot, recentChainData.getGenesisTime()).times(1000);
  }

  private void assertIsSlotCurrent(
      final UInt64 slot, final UInt64 currentTime, final boolean expectedResult) {
    final RecentChainData recentChainDataMock = mock(RecentChainData.class);
    final UpdatableStore storeMock = mock(UpdatableStore.class);
    when(recentChainDataMock.getStore()).thenReturn(storeMock);
    when(storeMock.getTimeInMillis()).thenReturn(currentTime);
    when(recentChainDataMock.getCurrentSlot()).thenReturn(Optional.of(slot));
    when(recentChainDataMock.getGenesisTimeMillis())
        .thenReturn(recentChainData.getGenesisTimeMillis());
    final GossipValidationHelper gossipValidationHelperMocked =
        new GossipValidationHelper(spec, recentChainDataMock, storageSystem.getMetricsSystem());
    assertThat(gossipValidationHelperMocked.isSlotCurrent(slot)).isEqualTo(expectedResult);
  }
}
