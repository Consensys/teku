/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class MergeTransitionBlockValidatorTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineChannel executionEngine = mock(ExecutionEngineChannel.class);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = false;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = true;
  }

  /**
   * The details of how we validate the merge block are all handled by {@link
   * BellatrixTransitionHelpers} so we don't want to retest all that here. Instead, we check that
   * those extra checks started (the call to {@link ExecutionEngineChannel#getPowBlock(Bytes32)}).
   *
   * <p>Since the future we return from getPowBlock is never completed we never complete the
   * validations which saves us a bunch of mocking.
   */
  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis(false);
    when(executionEngine.getPowBlock(any())).thenReturn(new SafeFuture<>());
  }

  @Test
  void shouldReturnValidImmediatelyWhenThereIsNoTransitionBlock() {
    final SignedBlockAndState chainHead = storageSystem.chainBuilder().getLatestBlockAndState();
    final SignedBlockAndState blockToVerify = storageSystem.chainBuilder().generateNextBlock();

    final MergeTransitionBlockValidator transitionVerifier =
        createTransitionValidator(blockToVerify.getBlock());
    final ExecutionPayload newExecutionPayload = getExecutionPayload(blockToVerify);

    assertThat(
            storageSystem
                .recentChainData()
                .getForkChoiceStrategy()
                .orElseThrow()
                .getOptimisticallySyncedTransitionBlockRoot(chainHead.getRoot()))
        .isPresent();

    final SafeFuture<PayloadStatus> result =
        transitionVerifier.verifyTransitionBlock(
            chainHead
                .getState()
                .toVersionBellatrix()
                .orElseThrow()
                .getLatestExecutionPayloadHeader(),
            newExecutionPayload);

    verifyNoInteractions(executionEngine);
    assertThat(result).isCompletedWithValue(PayloadStatus.VALID);
  }

  @Test
  void shouldVerifyNonFinalizedAncestorTransitionBlock() {
    final SignedBlockAndState transitionBlock = generateNonfinalizedTransition();
    final SignedBlockAndState chainHead = storageSystem.chainBuilder().getLatestBlockAndState();
    final SignedBlockAndState blockToVerify = storageSystem.chainBuilder().generateNextBlock();

    final MergeTransitionBlockValidator transitionVerifier =
        createTransitionValidator(blockToVerify.getBlock());
    final ExecutionPayload newExecutionPayload = getExecutionPayload(blockToVerify);

    assertThat(
            storageSystem
                .recentChainData()
                .getForkChoiceStrategy()
                .orElseThrow()
                .getOptimisticallySyncedTransitionBlockRoot(chainHead.getRoot()))
        .isPresent();

    final SafeFuture<PayloadStatus> result =
        transitionVerifier.verifyTransitionBlock(
            chainHead
                .getState()
                .toVersionBellatrix()
                .orElseThrow()
                .getLatestExecutionPayloadHeader(),
            newExecutionPayload);

    verify(executionEngine).getPowBlock(getExecutionPayload(transitionBlock).getParentHash());
    assertThat(result).isNotCompleted();
  }

  @Test
  void shouldVerifyFinalizedAncestorTransitionBlock() {
    final SignedBlockAndState transitionBlock = generateFinalizedTransition();
    final BeaconStateBellatrix chainHeadState =
        storageSystem
            .chainBuilder()
            .getLatestBlockAndState()
            .getState()
            .toVersionBellatrix()
            .orElseThrow();

    final SignedBlockAndState blockToVerify = storageSystem.chainBuilder().generateNextBlock();

    final MergeTransitionBlockValidator transitionVerifier =
        createTransitionValidator(blockToVerify.getBlock());
    final ExecutionPayload newExecutionPayload = getExecutionPayload(blockToVerify);

    assertThat(storageSystem.recentChainData().getStore().getFinalizedOptimisticTransitionPayload())
        .isPresent();

    final SafeFuture<PayloadStatus> result =
        transitionVerifier.verifyTransitionBlock(
            chainHeadState.getLatestExecutionPayloadHeader(), newExecutionPayload);

    verify(executionEngine).getPowBlock(getExecutionPayload(transitionBlock).getParentHash());
    assertThat(result).isNotCompleted();
  }

  private SignedBlockAndState generateNonfinalizedTransition() {
    storageSystem
        .chainBuilder()
        .generateBlocksUpToSlot(5)
        .forEach(storageSystem.chainUpdater()::saveOptimisticBlock);
    final Bytes32 terminalBlockHash = dataStructureUtil.randomBytes32();
    final SignedBlockAndState transitionBlock =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(6, BlockOptions.create().setTerminalBlockHash(terminalBlockHash));
    storageSystem.chainUpdater().saveOptimisticBlock(transitionBlock);

    storageSystem
        .chainBuilder()
        .generateBlocksUpToSlot(39)
        .forEach(storageSystem.chainUpdater()::saveOptimisticBlock);
    final SignedBlockAndState chainHead = storageSystem.chainBuilder().generateBlockAtSlot(40);
    storageSystem.chainUpdater().saveOptimisticBlock(chainHead);
    return transitionBlock;
  }

  private SignedBlockAndState generateFinalizedTransition() {
    final SignedBlockAndState transitionBlock = generateNonfinalizedTransition();

    // Finalize between transition block and chain head
    storageSystem.chainUpdater().finalizeEpoch(1);
    return transitionBlock;
  }

  private ExecutionPayload getExecutionPayload(final SignedBlockAndState blockToVerify) {
    return blockToVerify
        .getBlock()
        .getMessage()
        .getBody()
        .getOptionalExecutionPayload()
        .orElseThrow();
  }

  private MergeTransitionBlockValidator createTransitionValidator(final SignedBeaconBlock block) {
    return new MergeTransitionBlockValidator(
        spec, storageSystem.recentChainData(), executionEngine, block);
  }
}
