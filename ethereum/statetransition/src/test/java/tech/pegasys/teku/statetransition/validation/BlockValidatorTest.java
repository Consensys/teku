/*
 * Copyright Consensys Software Inc., 2023
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.BlockValidator.BroadcastValidationResult;

public class BlockValidatorTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BlockGossipValidator blockGossipValidator = mock(BlockGossipValidator.class);

  private final BlockValidator blockValidator = new BlockValidator(blockGossipValidator);

  final SafeFuture<BlockImportResult> consensusValidationResult = new SafeFuture<>();

  @Test
  public void shouldExposeGossipValidation() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    when(blockGossipValidator.validate(eq(block), eq(false)))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    assertThat(blockValidator.validateGossip(block))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    verify(blockGossipValidator).validate(eq(block), eq(false));
    verifyNoMoreInteractions(blockGossipValidator);
  }

  @Test
  public void shouldReturnSuccessWhenValidationIsGossipAndGossipValidationReturnsAccept() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    when(blockGossipValidator.validate(eq(block), eq(true)))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    assertThat(
            blockValidator.validateBroadcast(
                block, BroadcastValidationLevel.GOSSIP, consensusValidationResult))
        .isCompletedWithValueMatching(result -> result.equals(BroadcastValidationResult.SUCCESS));
    verify(blockGossipValidator).validate(eq(block), eq(true));
    verifyNoMoreInteractions(blockGossipValidator);
  }

  @ParameterizedTest
  @MethodSource("provideBroadcastValidationsAndGossipFailures")
  public void shouldReturnGossipFailureImmediatelyWhenGossipValidationIsNotAccept(
      final BroadcastValidationLevel broadcastValidation,
      final InternalValidationResult internalValidationResult) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    if (broadcastValidation == BroadcastValidationLevel.NOT_REQUIRED) {
      assertThat(
              blockValidator.validateBroadcast(
                  block, broadcastValidation, consensusValidationResult))
          .isCompletedWithValueMatching(result -> result.equals(BroadcastValidationResult.SUCCESS));
      verifyNoMoreInteractions(blockGossipValidator);
      return;
    }

    when(blockGossipValidator.validate(eq(block), eq(true)))
        .thenReturn(SafeFuture.completedFuture(internalValidationResult));

    assertThat(
            blockValidator.validateBroadcast(block, broadcastValidation, consensusValidationResult))
        .isCompletedWithValueMatching(
            result -> result.equals(BroadcastValidationResult.GOSSIP_FAILURE));
    verify(blockGossipValidator).validate(eq(block), eq(true));
    verifyNoMoreInteractions(blockGossipValidator);
  }

  @ParameterizedTest
  @EnumSource(
      value = BroadcastValidationLevel.class,
      names = {"CONSENSUS", "CONSENSUS_AND_EQUIVOCATION"})
  public void shouldReturnConsensusFailureImmediatelyWhenConsensusValidationIsNotSuccessful(
      final BroadcastValidationLevel broadcastValidation) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    when(blockGossipValidator.validate(eq(block), eq(true)))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    consensusValidationResult.complete(
        BlockImportResult.failedStateTransition(new RuntimeException("error")));

    assertThat(
            blockValidator.validateBroadcast(block, broadcastValidation, consensusValidationResult))
        .isCompletedWithValueMatching(
            result -> result.equals(BroadcastValidationResult.CONSENSUS_FAILURE));
    verify(blockGossipValidator).validate(eq(block), eq(true));
    verifyNoMoreInteractions(blockGossipValidator);
  }

  @ParameterizedTest
  @EnumSource(
      value = BroadcastValidationLevel.class,
      names = {"CONSENSUS", "CONSENSUS_AND_EQUIVOCATION"})
  public void shouldReturnConsensusFailureImmediatelyWhenConsensusCompleteExceptionally(
      final BroadcastValidationLevel broadcastValidation) {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    when(blockGossipValidator.validate(eq(block), eq(true)))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    consensusValidationResult.completeExceptionally(new RuntimeException("error"));

    assertThat(
            blockValidator.validateBroadcast(block, broadcastValidation, consensusValidationResult))
        .isCompletedExceptionally();
    verify(blockGossipValidator).validate(eq(block), eq(true));
    verifyNoMoreInteractions(blockGossipValidator);
  }

  @Test
  public void shouldReturnSuccessWhenSecondEquivocationCheckIsValidated() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    when(blockGossipValidator.validate(eq(block), eq(true)))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    consensusValidationResult.complete(BlockImportResult.successful(block));

    when(blockGossipValidator.blockIsFirstBlockWithValidSignatureForSlot(eq(block)))
        .thenReturn(Boolean.valueOf(true));

    assertThat(
            blockValidator.validateBroadcast(
                block,
                BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION,
                consensusValidationResult))
        .isCompletedWithValueMatching(result -> result.equals(BroadcastValidationResult.SUCCESS));
    verify(blockGossipValidator).validate(eq(block), eq(true));
    verify(blockGossipValidator).blockIsFirstBlockWithValidSignatureForSlot(eq(block));
    verifyNoMoreInteractions(blockGossipValidator);
  }

  @Test
  public void shouldReturnFinalEquivocationFailureWhenSecondEquivocationCheckFails() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    when(blockGossipValidator.validate(eq(block), eq(true)))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    consensusValidationResult.complete(BlockImportResult.successful(block));

    when(blockGossipValidator.blockIsFirstBlockWithValidSignatureForSlot(eq(block)))
        .thenReturn(Boolean.valueOf(false));

    assertThat(
            blockValidator.validateBroadcast(
                block,
                BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION,
                consensusValidationResult))
        .isCompletedWithValueMatching(
            result -> result.equals(BroadcastValidationResult.FINAL_EQUIVOCATION_FAILURE));
    verify(blockGossipValidator).validate(eq(block), eq(true));
    verify(blockGossipValidator).blockIsFirstBlockWithValidSignatureForSlot(eq(block));
    verifyNoMoreInteractions(blockGossipValidator);
  }

  private static Stream<Arguments> provideBroadcastValidationsAndGossipFailures() {
    return Arrays.stream(BroadcastValidationLevel.values())
        .flatMap(
            broadcastValidation ->
                Stream.of(
                    Arguments.of(broadcastValidation, InternalValidationResult.IGNORE),
                    Arguments.of(broadcastValidation, InternalValidationResult.SAVE_FOR_FUTURE)));
  }
}
