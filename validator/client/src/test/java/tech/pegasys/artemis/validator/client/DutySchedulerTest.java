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

package tech.pegasys.artemis.validator.client;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.api.ValidatorDuties;
import tech.pegasys.artemis.validator.client.duties.AttestationProductionDuty;
import tech.pegasys.artemis.validator.client.duties.BlockProductionDuty;
import tech.pegasys.artemis.validator.client.duties.ValidatorDutyFactory;
import tech.pegasys.artemis.validator.client.signer.Signer;

class DutySchedulerTest {
  private static final BLSPublicKey VALIDATOR1_KEY = BLSPublicKey.random(100);
  private static final BLSPublicKey VALIDATOR2_KEY = BLSPublicKey.random(200);
  private static final Collection<BLSPublicKey> VALIDATOR_KEYS =
      Set.of(VALIDATOR1_KEY, VALIDATOR2_KEY);
  private final Signer validator1Signer = mock(Signer.class);
  private final Signer validator2Signer = mock(Signer.class);
  private final Validator validator1 = new Validator(VALIDATOR1_KEY, validator1Signer);
  private final Validator validator2 = new Validator(VALIDATOR2_KEY, validator2Signer);

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorDutyFactory dutyFactory = mock(ValidatorDutyFactory.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final DutyScheduler validatorClient =
      new DutyScheduler(
          asyncRunner,
          validatorApiChannel,
          dutyFactory,
          Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2));

  @BeforeEach
  public void setUp() {
    when(validatorApiChannel.getDuties(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(emptyList())));
    when(dutyFactory.createAttestationProductionDuty(any()))
        .thenReturn(mock(AttestationProductionDuty.class));
  }

  @Test
  public void shouldFetchDutiesForCurrentAndNextEpoch() {
    validatorClient.onSlot(compute_start_slot_at_epoch(UnsignedLong.ONE));

    verify(validatorApiChannel).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(2), VALIDATOR_KEYS);
  }

  @Test
  public void shouldFetchDutiresForSecondEpochWhenFirstEpochReached() {
    validatorClient.onSlot(UnsignedLong.ZERO);

    verify(validatorApiChannel).getDuties(UnsignedLong.ZERO, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);

    // Process each slot up to the start of epoch 1
    final UnsignedLong epoch1Start = compute_start_slot_at_epoch(UnsignedLong.ONE);
    for (int slot = 0; slot <= epoch1Start.intValue(); slot++) {
      validatorClient.onSlot(UnsignedLong.valueOf(slot));
    }
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(2), VALIDATOR_KEYS);
  }

  @Test
  public void shouldNotRefetchDutiesWhichHaveAlreadyBeenRetrieved() {
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    validatorClient.onSlot(compute_start_slot_at_epoch(UnsignedLong.ONE));

    verify(validatorApiChannel).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(2), VALIDATOR_KEYS);

    validatorClient.onSlot(compute_start_slot_at_epoch(UnsignedLong.valueOf(2)));

    // Requests the next epoch, but not the current one because we already have that
    verify(validatorApiChannel).getDuties(UnsignedLong.valueOf(3), VALIDATOR_KEYS);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldRetryWhenRequestingDutiesFails() {
    final SafeFuture<Optional<List<ValidatorDuties>>> request1 = new SafeFuture<>();
    final SafeFuture<Optional<List<ValidatorDuties>>> request2 = new SafeFuture<>();
    when(validatorApiChannel.getDuties(UnsignedLong.ONE, VALIDATOR_KEYS))
        .thenReturn(request1)
        .thenReturn(request2);

    validatorClient.onSlot(compute_start_slot_at_epoch(UnsignedLong.ONE));
    verify(validatorApiChannel, times(1)).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);

    request1.completeExceptionally(new RuntimeException("Nope"));
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();

    // Should retry request
    verify(validatorApiChannel, times(2)).getDuties(UnsignedLong.ONE, VALIDATOR_KEYS);

    // And not have any more retries scheduled
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  public void shouldScheduleBlockProposalDuty() {
    final UnsignedLong blockProposerSlot = UnsignedLong.valueOf(5);
    final ValidatorDuties validator1Duties =
        ValidatorDuties.withDuties(
            VALIDATOR1_KEY, 5, 3, 6, List.of(blockProposerSlot), UnsignedLong.valueOf(7));
    when(validatorApiChannel.getDuties(eq(UnsignedLong.ONE), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(List.of(validator1Duties))));

    final BlockProductionDuty blockCreationDuty = mock(BlockProductionDuty.class);
    when(dutyFactory.createBlockProductionDuty(validator1, blockProposerSlot))
        .thenReturn(blockCreationDuty);

    // Load duties
    validatorClient.onSlot(compute_start_slot_at_epoch(UnsignedLong.ONE));

    // Execute
    validatorClient.onBlockProductionDue(blockProposerSlot);
    verify(blockCreationDuty).performDuty();
  }

  @Test
  public void shouldNotPerformDutiesForSameSlotTwice() {
    final UnsignedLong blockProposerSlot = UnsignedLong.valueOf(5);
    final ValidatorDuties validator1Duties =
        ValidatorDuties.withDuties(
            VALIDATOR1_KEY, 5, 3, 6, List.of(blockProposerSlot), UnsignedLong.valueOf(7));
    when(validatorApiChannel.getDuties(eq(UnsignedLong.ONE), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(List.of(validator1Duties))));

    final BlockProductionDuty blockCreationDuty = mock(BlockProductionDuty.class);
    when(dutyFactory.createBlockProductionDuty(validator1, blockProposerSlot))
        .thenReturn(blockCreationDuty);

    // Load duties
    validatorClient.onSlot(compute_start_slot_at_epoch(UnsignedLong.ONE));

    // Execute
    validatorClient.onBlockProductionDue(blockProposerSlot);
    verify(blockCreationDuty).performDuty();

    // Somehow we triggered the same slot again.
    validatorClient.onBlockProductionDue(blockProposerSlot);
    // But shouldn't produce another block and get ourselves slashed.
    verifyNoMoreInteractions(blockCreationDuty);
  }

  @Test
  public void shouldScheduleAttestationDuties() {
    final UnsignedLong attestationSlot = UnsignedLong.valueOf(5);
    final int validator1Index = 5;
    final int validator1Committee = 3;
    final int validator1CommitteePosition = 9;
    final int validator2Index = 6;
    final int validator2Committee = 4;
    final int validator2CommitteePosition = 8;
    final ValidatorDuties validator1Duties =
        ValidatorDuties.withDuties(
            VALIDATOR1_KEY,
            validator1Index,
            validator1Committee,
            validator1CommitteePosition,
            emptyList(),
            attestationSlot);
    final ValidatorDuties validator2Duties =
        ValidatorDuties.withDuties(
            VALIDATOR2_KEY,
            validator2Index,
            validator2Committee,
            validator2CommitteePosition,
            emptyList(),
            attestationSlot);
    when(validatorApiChannel.getDuties(eq(UnsignedLong.ONE), any()))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(List.of(validator1Duties, validator2Duties))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);

    when(dutyFactory.createAttestationProductionDuty(attestationSlot)).thenReturn(attestationDuty);

    // Load duties
    validatorClient.onSlot(compute_start_slot_at_epoch(UnsignedLong.ONE));

    // Both validators should be scheduled to create an attestation in the same slot
    verify(attestationDuty).addValidator(validator1Committee, validator1, validator1Index);
    verify(attestationDuty).addValidator(validator2Committee, validator2, validator2Index);

    // Execute
    validatorClient.onAttestationCreationDue(attestationSlot);
    verify(attestationDuty).performDuty();
  }
}
