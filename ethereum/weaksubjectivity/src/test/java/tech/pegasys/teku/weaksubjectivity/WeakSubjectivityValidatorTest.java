/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.weaksubjectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.weaksubjectivity.policies.WeakSubjectivityViolationPolicy;

public class WeakSubjectivityValidatorTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  // Set up mocks
  private final WeakSubjectivityCalculator calculator = mock(WeakSubjectivityCalculator.class);
  private final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
  private final List<WeakSubjectivityViolationPolicy> policies =
      List.of(
          mock(WeakSubjectivityViolationPolicy.class), mock(WeakSubjectivityViolationPolicy.class));
  private final InOrder orderedPolicyMocks = inOrder(policies.get(0), policies.get(1));
  private final CheckpointState checkpointState = mock(CheckpointState.class);
  private final UInt64 currentSlot = UInt64.valueOf(10_000);

  @BeforeEach
  public void setup() {
    when(checkpointState.getState()).thenReturn(mock(BeaconState.class));
  }

  @Test
  public void validateLatestFinalizedCheckpoint_noWSCheckpoint_validationShouldFail() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.empty());

    final int validatorCount = 101;
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(false);
    when(calculator.getActiveValidators(checkpointState.getState())).thenReturn(validatorCount);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    orderedPolicyMocks
        .verify(policies.get(0))
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            checkpointState, validatorCount, currentSlot);
    orderedPolicyMocks
        .verify(policies.get(1))
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            checkpointState, validatorCount, currentSlot);

    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void validateLatestFinalizedCheckpoint_noWSCheckpoint_validationShouldPass() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.empty());
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(true);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(calculator).isWithinWeakSubjectivityPeriod(checkpointState, currentSlot);
    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldSkipChecksWhenFinalizePriorToCheckpoint() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));
    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch().minus(1));

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(calculator, never()).getActiveValidators(any());
    verify(calculator, never()).isWithinWeakSubjectivityPeriod(any(), any());
    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldRunChecksWhenFinalizeAfterCheckpoint_shouldFail() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));

    final int validatorCount = 101;
    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch().plus(1));
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(false);
    when(calculator.getActiveValidators(checkpointState.getState())).thenReturn(validatorCount);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    orderedPolicyMocks
        .verify(policies.get(0))
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            checkpointState, validatorCount, currentSlot);
    orderedPolicyMocks
        .verify(policies.get(1))
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            checkpointState, validatorCount, currentSlot);

    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldRunChecksWhenFinalizeAfterCheckpoint_shouldPass() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));
    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch().plus(1));
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(true);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(calculator).isWithinWeakSubjectivityPeriod(checkpointState, currentSlot);
    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldRunChecksWhenFinalizeAtCheckpoint_shouldFail() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));

    final int validatorCount = 101;
    // Checkpoint is at the ws epoch, but has a different root
    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch());
    when(checkpointState.getRoot()).thenReturn(Bytes32.fromHexStringLenient("0x02"));
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(true);
    when(calculator.getActiveValidators(checkpointState.getState())).thenReturn(validatorCount);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    orderedPolicyMocks
        .verify(policies.get(0))
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            checkpointState, validatorCount, currentSlot);
    orderedPolicyMocks
        .verify(policies.get(1))
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            checkpointState, validatorCount, currentSlot);

    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldRunChecksWhenFinalizeAtCheckpoint_shouldPass() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));
    // Checkpoint is at the ws epoch, with the same root
    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch());
    when(checkpointState.getRoot()).thenReturn(wsCheckpoint.getRoot());
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(true);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(calculator).isWithinWeakSubjectivityPeriod(checkpointState, currentSlot);
    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void validateIsConsistentWithWSCheckpoint_noCheckpointSet() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.empty());
    final CombinedChainDataClient chainData = mockChainDataClientPriorToCheckpoint();

    SafeFuture<Void> result = validator.validateIsConsistentWithWSCheckpoint(chainData);

    assertThat(result).isCompleted();
    orderedPolicyMocks.verifyNoMoreInteractions();
    verify(chainData, never()).isFinalizedEpoch(any());
  }

  @Test
  public void validateIsConsistentWithWSCheckpoint_checkpointNotFinalized() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));
    final CombinedChainDataClient chainData = mockChainDataClientPriorToCheckpoint();

    SafeFuture<Void> result = validator.validateIsConsistentWithWSCheckpoint(chainData);

    assertThat(result).isCompleted();
    orderedPolicyMocks.verifyNoMoreInteractions();
    verify(chainData).isFinalizedEpoch(wsCheckpoint.getEpoch());
  }

  @Test
  public void validateIsConsistentWithWSCheckpoint_checkpointFinalizedWithMatchingBlock() {
    final UInt64 checkpointEpoch = UInt64.valueOf(100);
    final UInt64 checkpointSlot = compute_start_slot_at_epoch(checkpointEpoch);
    final SignedBeaconBlock checkpointBlock =
        dataStructureUtil.randomSignedBeaconBlock(checkpointSlot);
    final Checkpoint wsCheckpoint = new Checkpoint(checkpointEpoch, checkpointBlock.getRoot());
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));
    final CombinedChainDataClient chainData =
        mockChainDataClientAfterCheckpoint(wsCheckpoint, checkpointBlock);

    SafeFuture<Void> result = validator.validateIsConsistentWithWSCheckpoint(chainData);

    assertThat(result).isCompleted();
    orderedPolicyMocks.verifyNoMoreInteractions();
    verify(chainData).isFinalizedEpoch(wsCheckpoint.getEpoch());
    verify(chainData).getBlockInEffectAtSlot(wsCheckpoint.getEpochStartSlot());
  }

  @Test
  public void validateIsConsistentWithWSCheckpoint_checkpointFinalizedWithNonMatchingBlock() {
    final UInt64 checkpointEpoch = UInt64.valueOf(100);
    final UInt64 checkpointSlot = compute_start_slot_at_epoch(checkpointEpoch);
    final SignedBeaconBlock checkpointBlock =
        dataStructureUtil.randomSignedBeaconBlock(checkpointSlot);
    final SignedBeaconBlock otherBlock = dataStructureUtil.randomSignedBeaconBlock(checkpointSlot);
    final Checkpoint wsCheckpoint = new Checkpoint(checkpointEpoch, checkpointBlock.getRoot());
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));
    final CombinedChainDataClient chainData =
        mockChainDataClientAfterCheckpoint(wsCheckpoint, otherBlock);

    SafeFuture<Void> result = validator.validateIsConsistentWithWSCheckpoint(chainData);

    assertThat(result).isCompleted();
    verify(chainData).isFinalizedEpoch(wsCheckpoint.getEpoch());
    verify(chainData).getBlockInEffectAtSlot(wsCheckpoint.getEpochStartSlot());

    orderedPolicyMocks
        .verify(policies.get(0))
        .onChainInconsistentWithWeakSubjectivityCheckpoint(wsCheckpoint, otherBlock);
    orderedPolicyMocks
        .verify(policies.get(1))
        .onChainInconsistentWithWeakSubjectivityCheckpoint(wsCheckpoint, otherBlock);
    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void isBlockValid_noWSCheckpoint() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.empty());
    final SignedBeaconBlock block =
        mockBlock(10, Bytes32.fromHexString("0x02"), Bytes32.fromHexString("0x01"));

    assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isTrue();
  }

  @Test
  public void isBlockValid_withWSCheckpoint_blockPriorToCheckpoint() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));
    final SignedBeaconBlock block =
        mockBlock(10, Bytes32.fromHexString("0x02"), Bytes32.fromHexString("0x01"));

    assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isTrue();
  }

  @Test
  public void isBlockValid_withWSCheckpoint_blockAtCheckpointEpochBoundary_matches() {
    final UInt64 wsCheckpointEpoch = UInt64.valueOf(100);
    final UInt64 wsCheckpointSlot = compute_start_slot_at_epoch(wsCheckpointEpoch);
    final Checkpoint wsCheckpoint =
        new Checkpoint(wsCheckpointEpoch, Bytes32.fromHexStringLenient("0x0100"));
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));
    final SignedBeaconBlock block =
        mockBlock(wsCheckpointSlot, wsCheckpoint.getRoot(), Bytes32.fromHexString("0x01"));

    assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isTrue();
  }

  @Test
  public void isBlockValid_withWSCheckpoint_blockAtCheckpointEpochBoundary_mismatch() {
    final UInt64 wsCheckpointEpoch = UInt64.valueOf(100);
    final UInt64 wsCheckpointSlot = compute_start_slot_at_epoch(wsCheckpointEpoch);
    final Checkpoint wsCheckpoint =
        new Checkpoint(wsCheckpointEpoch, Bytes32.fromHexStringLenient("0x0100"));
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));
    final SignedBeaconBlock block =
        mockBlock(wsCheckpointSlot, Bytes32.fromHexString("0x02"), Bytes32.fromHexString("0x01"));

    assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isFalse();
  }

  @Test
  public void isBlockValid_withWSCheckpoint_blockAfterCheckpointEpochBoundary_matchingAncestor() {
    final UInt64 wsCheckpointEpoch = UInt64.valueOf(100);
    final UInt64 wsCheckpointSlot = compute_start_slot_at_epoch(wsCheckpointEpoch);
    final SignedBeaconBlock checkpointBlock =
        dataStructureUtil.randomSignedBeaconBlock(wsCheckpointSlot);

    final Checkpoint wsCheckpoint = new Checkpoint(wsCheckpointEpoch, checkpointBlock.getRoot());
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));

    final List<SignedBeaconBlock> blocks =
        dataStructureUtil.randomSignedBeaconBlockSequence(checkpointBlock, 3);
    mockForkChoice(checkpointBlock);
    mockForkChoice(blocks);

    for (SignedBeaconBlock block : blocks) {
      assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isTrue();
    }
  }

  @Test
  public void isBlockValid_withWSCheckpoint_blockAfterCheckpointEpochBoundary_mismatchedAncestor() {
    final UInt64 wsCheckpointEpoch = UInt64.valueOf(100);
    final UInt64 wsCheckpointSlot = compute_start_slot_at_epoch(wsCheckpointEpoch);
    final SignedBeaconBlock checkpointBlock =
        dataStructureUtil.randomSignedBeaconBlock(wsCheckpointSlot);
    final SignedBeaconBlock conflictingBlock =
        dataStructureUtil.randomSignedBeaconBlock(wsCheckpointSlot);

    final Checkpoint wsCheckpoint = new Checkpoint(wsCheckpointEpoch, checkpointBlock.getRoot());
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.of(wsCheckpoint));

    final List<SignedBeaconBlock> blocks =
        dataStructureUtil.randomSignedBeaconBlockSequence(conflictingBlock, 3);
    mockForkChoice(conflictingBlock);
    mockForkChoice(blocks);

    for (SignedBeaconBlock block : blocks) {
      assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isFalse();
    }
  }

  @Test
  public void handleValidationFailure() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.empty());

    final String message = "Oops";
    validator.handleValidationFailure(message);

    orderedPolicyMocks.verify(policies.get(0)).onFailedToPerformValidation(message);
    orderedPolicyMocks.verify(policies.get(1)).onFailedToPerformValidation(message);

    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void handleValidationFailure_withThrowable() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(calculator, policies, Optional.empty());

    final String message = "Oops";
    final Throwable error = new RuntimeException("fail");
    validator.handleValidationFailure(message, error);

    orderedPolicyMocks.verify(policies.get(0)).onFailedToPerformValidation(message, error);
    orderedPolicyMocks.verify(policies.get(1)).onFailedToPerformValidation(message, error);

    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  private SignedBeaconBlock mockBlock(
      final long slot, final Bytes32 root, final Bytes32 parentRoot) {
    return mockBlock(UInt64.valueOf(slot), root, parentRoot);
  }

  private SignedBeaconBlock mockBlock(
      final UInt64 slot, final Bytes32 root, final Bytes32 parentRoot) {
    final SignedBeaconBlock block = mock(SignedBeaconBlock.class);
    when(block.getSlot()).thenReturn(slot);
    when(block.getRoot()).thenReturn(root);
    when(block.getParent_root()).thenReturn(parentRoot);

    return block;
  }

  private void mockForkChoice(SignedBeaconBlock... blocks) {
    mockForkChoice(Arrays.asList(blocks));
  }

  private void mockForkChoice(List<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock block : blocks) {
      when(forkChoiceStrategy.blockSlot(block.getRoot())).thenReturn(Optional.of(block.getSlot()));
      when(forkChoiceStrategy.blockParentRoot(block.getRoot()))
          .thenReturn(Optional.of(block.getParent_root()));
    }
  }

  private CombinedChainDataClient mockChainDataClientPriorToCheckpoint() {
    final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
    when(client.isFinalizedEpoch(any())).thenReturn(false);
    return client;
  }

  private CombinedChainDataClient mockChainDataClientAfterCheckpoint(
      final Checkpoint wsCheckpoint, final SignedBeaconBlock block) {
    final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
    when(client.isFinalizedEpoch(any())).thenReturn(true);
    when(client.getBlockInEffectAtSlot(wsCheckpoint.getEpochStartSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    return client;
  }
}
