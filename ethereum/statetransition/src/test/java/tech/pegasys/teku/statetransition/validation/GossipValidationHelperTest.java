/*
 * Copyright ConsenSys Software Inc., 2023
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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.statetransition.validation.GossipValidationHelper.MAX_OFFSET_TIME_IN_SECONDS;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext()
public class GossipValidationHelperTest {
  private Spec spec;
  private RecentChainData recentChainData;
  private DataStructureUtil dataStructureUtil;
  private StorageSystem storageSystem;

  private GossipValidationHelper gossipValidationHelper;

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.blsVerifyDeposit = false;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.blsVerifyDeposit = true;
  }

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis(false);
    recentChainData = storageSystem.recentChainData();

    gossipValidationHelper = new GossipValidationHelper(spec, recentChainData);
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

    final UInt64 slot2Time =
        spec.computeTimeAtSlot(
            recentChainData.getBestState().orElseThrow().getImmediately(), slot2);

    final UInt64 notYetInsideTolerance = slot2Time.minus(MAX_OFFSET_TIME_IN_SECONDS).minus(1);
    storageSystem.chainUpdater().setTime(notYetInsideTolerance);
    assertThat(gossipValidationHelper.isSlotFromFuture(slot2)).isTrue();

    final UInt64 insideTolerance = slot2Time.minus(MAX_OFFSET_TIME_IN_SECONDS);
    storageSystem.chainUpdater().setTime(insideTolerance);
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
    SafeFutureAssert.assertThatSafeFuture(
            gossipValidationHelper.getParentStateInBlockEpoch(
                lastBlockStateInEpoch0.getSlot(),
                lastBlockStateInEpoch0.getRoot(),
                lastSlotInEpoch0))
        .isCompletedWithValueMatching(
            beaconState -> beaconState.orElseThrow().equals(lastBlockStateInEpoch0.getState()));

    // should generate a state for epoch 1
    SafeFutureAssert.assertThatSafeFuture(
            gossipValidationHelper.getParentStateInBlockEpoch(
                lastBlockStateInEpoch0.getSlot(),
                lastBlockStateInEpoch0.getRoot(),
                firstSlotAtEpoch1))
        .isCompletedWithValueMatching(
            beaconState -> beaconState.orElseThrow().getSlot().equals(firstSlotAtEpoch1));
  }
}
