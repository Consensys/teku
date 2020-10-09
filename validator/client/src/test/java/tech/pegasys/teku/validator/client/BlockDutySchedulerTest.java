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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorDuties;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.BlockProductionDuty;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

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

  @Test
  public void shouldRefetchDutiesAfterReorg() {
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 currentSlot = compute_start_slot_at_epoch(currentEpoch);
    final UInt64 commonAncestorEpoch = currentEpoch.minus(1);
    final UInt64 commonAncestorSlot = compute_start_slot_at_epoch(commonAncestorEpoch);
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(currentSlot);

    verify(validatorApiChannel).getDuties(currentEpoch, VALIDATOR_KEYS);

    dutyScheduler.onChainReorg(currentSlot, commonAncestorSlot);

    verify(validatorApiChannel, times(2)).getDuties(currentEpoch, VALIDATOR_KEYS);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldNotRefetchDutiesWhenCommonAncestorInCurrentEpoch() {
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 currentSlot = compute_start_slot_at_epoch(currentEpoch);
    final UInt64 commonAncestorSlot = compute_start_slot_at_epoch(currentEpoch);
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(currentSlot);

    verify(validatorApiChannel).getDuties(currentEpoch, VALIDATOR_KEYS);

    dutyScheduler.onChainReorg(currentSlot, commonAncestorSlot);

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldRefetchAllDutiesOnMissedEvents() {
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 currentSlot = compute_start_slot_at_epoch(currentEpoch);
    when(validatorApiChannel.getDuties(any(), any())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(currentSlot);

    verify(validatorApiChannel).getDuties(currentEpoch, VALIDATOR_KEYS);

    dutyScheduler.onPossibleMissedEvents();

    // Remembers the previous latest epoch and uses it to recalculate duties
    verify(validatorApiChannel, times(2)).getDuties(currentEpoch, VALIDATOR_KEYS);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldRefetchAllDutiesOnMissedEventsWithNoPreviousEpoch() {
    dutyScheduler.onPossibleMissedEvents();

    // Latest epoch is unknown so can't recalculate duties
    verifyNoMoreInteractions(validatorApiChannel);
  }
}
