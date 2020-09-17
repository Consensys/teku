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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.weaksubjectivity.policies.WeakSubjectivityViolationPolicy;

public class WeakSubjectivityValidatorTest {

  // Set up mocks
  private final WeakSubjectivityCalculator calculator = mock(WeakSubjectivityCalculator.class);
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
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldSkipChecksWhenFinalizePriorToCheckpoint_shouldPass() {
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
}
