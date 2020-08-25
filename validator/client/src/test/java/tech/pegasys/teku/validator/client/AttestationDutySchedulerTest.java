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

package tech.pegasys.teku.validator.client;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.validator.api.ValidatorDuties;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

public class AttestationDutySchedulerTest extends AbstractDutySchedulerTest {
  private final AttestationDutyScheduler dutyScheduler =
      new AttestationDutyScheduler(
          metricsSystem,
          new RetryingDutyLoader(
              asyncRunner,
              new ValidatorApiDutyLoader(
                  metricsSystem,
                  validatorApiChannel,
                  forkProvider,
                  () -> new ScheduledDuties(dutyFactory),
                  Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2))),
          stableSubnetSubscriber);

  @Test
  public void shouldFetchDutiesForCurrentAndNextEpoch() {
    dutyScheduler.onSlot(compute_start_slot_at_epoch(UInt64.ONE));

    verify(validatorApiChannel).getDuties(UInt64.ONE, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UInt64.valueOf(2), VALIDATOR_KEYS);
  }

  @Test
  public void shouldFetchDutiesForSecondEpochWhenFirstEpochReached() {
    dutyScheduler.onSlot(ZERO);

    verify(validatorApiChannel).getDuties(ZERO, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UInt64.ONE, VALIDATOR_KEYS);

    // Process each slot up to the start of epoch 1
    final UInt64 epoch1Start = compute_start_slot_at_epoch(UInt64.ONE);
    for (int slot = 0; slot <= epoch1Start.intValue(); slot++) {
      dutyScheduler.onSlot(UInt64.valueOf(slot));
    }
    verify(validatorApiChannel).getDuties(UInt64.valueOf(2), VALIDATOR_KEYS);
  }

  @Test
  public void shouldNotRefetchDutiesWhichHaveAlreadyBeenRetrieved() {
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(compute_start_slot_at_epoch(UInt64.ONE));

    verify(validatorApiChannel).getDuties(UInt64.ONE, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UInt64.valueOf(2), VALIDATOR_KEYS);

    dutyScheduler.onSlot(compute_start_slot_at_epoch(UInt64.valueOf(2)));

    // Requests the next epoch, but not the current one because we already have that
    verify(validatorApiChannel).getDuties(UInt64.valueOf(3), VALIDATOR_KEYS);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldNotRefetchDutiesWhichHaveAlreadyBeenRetrievedDuringFirstEpoch() {
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(ZERO);

    verify(validatorApiChannel).getDuties(ZERO, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(ONE, VALIDATOR_KEYS);

    // Second slot in epoch 0
    dutyScheduler.onSlot(ONE);

    // Shouldn't request any more duties
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldRetryWhenRequestingDutiesFails() {
    final SafeFuture<Optional<List<ValidatorDuties>>> request1 = new SafeFuture<>();
    final SafeFuture<Optional<List<ValidatorDuties>>> request2 = new SafeFuture<>();
    when(validatorApiChannel.getDuties(UInt64.ONE, VALIDATOR_KEYS))
        .thenReturn(request1)
        .thenReturn(request2);

    dutyScheduler.onSlot(compute_start_slot_at_epoch(UInt64.ONE));
    verify(validatorApiChannel, times(1)).getDuties(UInt64.ONE, VALIDATOR_KEYS);

    request1.completeExceptionally(new RuntimeException("Nope"));
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();

    // Should retry request
    verify(validatorApiChannel, times(2)).getDuties(UInt64.ONE, VALIDATOR_KEYS);

    // And not have any more retries scheduled
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  public void shouldRefetchDutiesAfterReorg() {
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(compute_start_slot_at_epoch(UInt64.ONE));

    verify(validatorApiChannel).getDuties(UInt64.ONE, VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UInt64.valueOf(2), VALIDATOR_KEYS);

    dutyScheduler.onChainReorg(compute_start_slot_at_epoch(UInt64.valueOf(2)));

    // Re-requests epoch 2 and also requests epoch 3 as the new chain is far enough along for that
    verify(validatorApiChannel, times(2)).getDuties(UInt64.valueOf(2), VALIDATOR_KEYS);
    verify(validatorApiChannel).getDuties(UInt64.valueOf(3), VALIDATOR_KEYS);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldDelayExecutingDutiesUntilSchedulingIsComplete() {
    final ScheduledDuties scheduledDuties = mock(ScheduledDuties.class);
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();
    final ValidatorTimingChannel dutyScheduler =
        new AttestationDutyScheduler(
            metricsSystem,
            new RetryingDutyLoader(
                asyncRunner,
                new ValidatorApiDutyLoader(
                    metricsSystem,
                    validatorApiChannel,
                    forkProvider,
                    () -> scheduledDuties,
                    Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2))),
            stableSubnetSubscriber);
    final SafeFuture<Optional<List<ValidatorDuties>>> epoch0Duties = new SafeFuture<>();

    when(validatorApiChannel.getDuties(eq(ZERO), any())).thenReturn(epoch0Duties);
    when(validatorApiChannel.getDuties(eq(ONE), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(emptyList())));
    dutyScheduler.onSlot(ZERO);

    dutyScheduler.onBlockProductionDue(ZERO);
    dutyScheduler.onAttestationCreationDue(ZERO);
    dutyScheduler.onAttestationAggregationDue(ZERO);
    // Duties haven't been loaded yet.
    verify(scheduledDuties, never()).produceAttestations(ZERO);
    verify(scheduledDuties, never()).performAggregation(ZERO);

    epoch0Duties.complete(Optional.of(emptyList()));
    verify(scheduledDuties).produceAttestations(ZERO);
    verify(scheduledDuties).performAggregation(ZERO);
  }

  @Test
  public void shouldNotPerformDutiesForSameSlotTwice() {
    final UInt64 attestationProductionSlot = UInt64.valueOf(5);
    final ValidatorDuties validator1Duties =
        ValidatorDuties.withDuties(
            VALIDATOR1_KEY, 5, 3, 6, 0, List.of(), attestationProductionSlot);
    when(validatorApiChannel.getDuties(eq(ZERO), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(dutyFactory.createAttestationProductionDuty(attestationProductionSlot))
        .thenReturn(attestationDuty);

    // Load duties
    dutyScheduler.onSlot(compute_start_slot_at_epoch(ZERO));
    verify(attestationDuty).addValidator(validator1, 3, 6, 5);

    // Execute
    dutyScheduler.onAttestationCreationDue(attestationProductionSlot);
    verify(attestationDuty).performDuty();

    // Somehow we triggered the same slot again.
    dutyScheduler.onAttestationCreationDue(attestationProductionSlot);
    // But shouldn't produce another block and get ourselves slashed.
    verifyNoMoreInteractions(attestationDuty);
  }

  @Test
  public void shouldScheduleAttestationDuties() {
    final UInt64 attestationSlot = UInt64.valueOf(5);
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
            0,
            emptyList(),
            attestationSlot);
    final ValidatorDuties validator2Duties =
        ValidatorDuties.withDuties(
            VALIDATOR2_KEY,
            validator2Index,
            validator2Committee,
            validator2CommitteePosition,
            0,
            emptyList(),
            attestationSlot);
    when(validatorApiChannel.getDuties(eq(ZERO), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties, validator2Duties))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(dutyFactory.createAttestationProductionDuty(attestationSlot)).thenReturn(attestationDuty);

    // Load duties
    dutyScheduler.onSlot(compute_start_slot_at_epoch(ZERO));

    // Both validators should be scheduled to create an attestation in the same slot
    verify(attestationDuty)
        .addValidator(
            validator1, validator1Committee, validator1CommitteePosition, validator1Index);
    verify(attestationDuty)
        .addValidator(
            validator2, validator2Committee, validator2CommitteePosition, validator2Index);

    // Execute
    dutyScheduler.onAttestationCreationDue(attestationSlot);
    verify(attestationDuty).performDuty();
  }

  @Test
  public void shouldScheduleAttestationDutiesWhenBlockIsImported() {
    final UInt64 attestationSlot = UInt64.valueOf(5);
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
            0,
            emptyList(),
            attestationSlot);
    final ValidatorDuties validator2Duties =
        ValidatorDuties.withDuties(
            VALIDATOR2_KEY,
            validator2Index,
            validator2Committee,
            validator2CommitteePosition,
            0,
            emptyList(),
            attestationSlot);
    when(validatorApiChannel.getDuties(eq(ZERO), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties, validator2Duties))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(dutyFactory.createAttestationProductionDuty(attestationSlot)).thenReturn(attestationDuty);

    // Load duties
    dutyScheduler.onSlot(compute_start_slot_at_epoch(ZERO));

    // Both validators should be scheduled to create an attestation in the same slot
    verify(attestationDuty)
        .addValidator(
            validator1, validator1Committee, validator1CommitteePosition, validator1Index);
    verify(attestationDuty)
        .addValidator(
            validator2, validator2Committee, validator2CommitteePosition, validator2Index);

    // Execute
    dutyScheduler.onBlockImportedForSlot(attestationSlot);
    verify(attestationDuty).performDuty();
  }

  @Test
  public void shouldNotScheduleAttestationDutiesTwice() {
    final UInt64 attestationSlot = UInt64.valueOf(5);
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
            0,
            emptyList(),
            attestationSlot);
    final ValidatorDuties validator2Duties =
        ValidatorDuties.withDuties(
            VALIDATOR2_KEY,
            validator2Index,
            validator2Committee,
            validator2CommitteePosition,
            0,
            emptyList(),
            attestationSlot);
    when(validatorApiChannel.getDuties(eq(ZERO), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties, validator2Duties))));

    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(attestationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(dutyFactory.createAttestationProductionDuty(attestationSlot)).thenReturn(attestationDuty);

    // Load duties
    dutyScheduler.onSlot(compute_start_slot_at_epoch(ZERO));

    // Both validators should be scheduled to create an attestation in the same slot
    verify(attestationDuty)
        .addValidator(
            validator1, validator1Committee, validator1CommitteePosition, validator1Index);
    verify(attestationDuty)
        .addValidator(
            validator2, validator2Committee, validator2CommitteePosition, validator2Index);

    // Execute
    dutyScheduler.onBlockImportedForSlot(attestationSlot);
    verify(attestationDuty).performDuty();

    dutyScheduler.onAttestationCreationDue(attestationSlot);
    verifyNoMoreInteractions(attestationDuty);
  }

  @Test
  public void shouldScheduleAggregationDuties() {
    final UInt64 attestationSlot = UInt64.valueOf(13);
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
            1, // Guaranteed to be an aggregator
            emptyList(),
            attestationSlot);
    final ValidatorDuties validator2Duties =
        ValidatorDuties.withDuties(
            VALIDATOR2_KEY,
            validator2Index,
            validator2Committee,
            validator2CommitteePosition,
            100000, // Won't be an aggregator
            emptyList(),
            attestationSlot);
    when(validatorApiChannel.getDuties(eq(ONE), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties, validator2Duties))));

    final BLSSignature validator1Signature = dataStructureUtil.randomSignature();
    when(validator1.getSigner().signAggregationSlot(attestationSlot, fork))
        .thenReturn(completedFuture(validator1Signature));
    when(validator2.getSigner().signAggregationSlot(attestationSlot, fork))
        .thenReturn(completedFuture(dataStructureUtil.randomSignature()));

    final SafeFuture<Optional<Attestation>> unsignedAttestationFuture = new SafeFuture<>();
    final AggregationDuty aggregationDuty = mock(AggregationDuty.class);
    final AttestationProductionDuty attestationDuty = mock(AttestationProductionDuty.class);
    when(dutyFactory.createAttestationProductionDuty(attestationSlot)).thenReturn(attestationDuty);
    when(dutyFactory.createAggregationDuty(attestationSlot)).thenReturn(aggregationDuty);
    when(aggregationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(attestationDuty.addValidator(
            validator1, validator1Committee, validator1CommitteePosition, validator1Index))
        .thenReturn(unsignedAttestationFuture);

    // Load duties
    final UInt64 epochStartSlot = compute_start_slot_at_epoch(ONE);
    dutyScheduler.onSlot(epochStartSlot);

    // Only validator1 should have had an aggregation duty created for it
    verify(dutyFactory).createAggregationDuty(attestationSlot);
    // And should have added validator1 to each duty
    verify(aggregationDuty)
        .addValidator(
            validator1,
            validator1Index,
            validator1Signature,
            validator1Committee,
            unsignedAttestationFuture);
    verifyNoMoreInteractions(aggregationDuty);

    // Perform the duties
    dutyScheduler.onAttestationAggregationDue(attestationSlot);
    verify(aggregationDuty).performDuty();
  }
}
