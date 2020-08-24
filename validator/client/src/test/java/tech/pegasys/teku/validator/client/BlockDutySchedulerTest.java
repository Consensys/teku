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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.validator.api.ValidatorDuties;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.duties.BlockProductionDuty;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

public class BlockDutySchedulerTest extends AbstractDutySchedulerTest {
  private final BlockDutyScheduler dutyScheduler =
      new BlockDutyScheduler(
          metricsSystem,
          new RetryingDutyLoader(
              asyncRunner,
              new ValidatorApiDutyLoader(
                  metricsSystem,
                  validatorApiChannel,
                  forkProvider,
                  () -> new ScheduledDuties(dutyFactory),
                  Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2))));

  @Test
  public void shouldFetchDutiesForCurrentEpoch() {
    dutyScheduler.onSlot(compute_start_slot_at_epoch(UInt64.ONE));

    verify(validatorApiChannel).getDuties(UInt64.ONE, VALIDATOR_KEYS);
    verify(validatorApiChannel, never()).getDuties(UInt64.valueOf(2), VALIDATOR_KEYS);
  }

  @Test
  public void shouldNotPerformDutiesForSameSlotTwice() {
    final UInt64 blockProposerSlot = UInt64.valueOf(5);
    final ValidatorDuties validator1Duties =
        ValidatorDuties.withDuties(
            VALIDATOR1_KEY, 5, 3, 6, 0, List.of(blockProposerSlot), UInt64.valueOf(7));
    when(validatorApiChannel.getDuties(eq(ZERO), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties))));

    final BlockProductionDuty blockCreationDuty = mock(BlockProductionDuty.class);
    when(blockCreationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(dutyFactory.createBlockProductionDuty(blockProposerSlot, validator1))
        .thenReturn(blockCreationDuty);

    // Load duties
    dutyScheduler.onSlot(compute_start_slot_at_epoch(ZERO));

    // Execute
    dutyScheduler.onBlockProductionDue(blockProposerSlot);
    verify(blockCreationDuty).performDuty();

    // Somehow we triggered the same slot again.
    dutyScheduler.onBlockProductionDue(blockProposerSlot);
    // But shouldn't produce another block and get ourselves slashed.
    verifyNoMoreInteractions(blockCreationDuty);
  }

  @Test
  public void shouldScheduleBlockProposalDuty() {
    final UInt64 blockProposerSlot = UInt64.valueOf(5);
    final ValidatorDuties validator1Duties =
        ValidatorDuties.withDuties(
            VALIDATOR1_KEY, 5, 3, 6, 0, List.of(blockProposerSlot), UInt64.valueOf(7));
    when(validatorApiChannel.getDuties(eq(ZERO), any()))
        .thenReturn(completedFuture(Optional.of(List.of(validator1Duties))));

    final BlockProductionDuty blockCreationDuty = mock(BlockProductionDuty.class);
    when(blockCreationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(dutyFactory.createBlockProductionDuty(blockProposerSlot, validator1))
        .thenReturn(blockCreationDuty);

    // Load duties
    dutyScheduler.onSlot(compute_start_slot_at_epoch(ZERO));

    // Execute
    dutyScheduler.onBlockProductionDue(blockProposerSlot);
    verify(blockCreationDuty).performDuty();
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
    verify(attestationDuty, never()).performDuty();

    dutyScheduler.onAttestationCreationDue(attestationSlot);
    verifyNoMoreInteractions(attestationDuty);
  }

  @Test
  public void shouldDelayExecutingDutiesUntilSchedulingIsComplete() {
    final ScheduledDuties scheduledDuties = mock(ScheduledDuties.class);
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();
    final ValidatorTimingChannel dutyScheduler =
        new BlockDutyScheduler(
            metricsSystem,
            new RetryingDutyLoader(
                asyncRunner,
                new ValidatorApiDutyLoader(
                    metricsSystem,
                    validatorApiChannel,
                    forkProvider,
                    () -> scheduledDuties,
                    Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2))));
    final SafeFuture<Optional<List<ValidatorDuties>>> epoch0Duties = new SafeFuture<>();

    when(validatorApiChannel.getDuties(eq(ZERO), any())).thenReturn(epoch0Duties);
    when(validatorApiChannel.getDuties(eq(ONE), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(emptyList())));
    dutyScheduler.onSlot(ZERO);

    dutyScheduler.onBlockProductionDue(ZERO);
    // Duties haven't been loaded yet.
    verify(scheduledDuties, never()).produceBlock(ZERO);

    epoch0Duties.complete(Optional.of(emptyList()));
    verify(scheduledDuties).produceBlock(ZERO);
  }
}
