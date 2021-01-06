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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

class EpochDutiesTest {

  private static final UInt64 EPOCH = UInt64.valueOf(10);
  private final SafeFuture<ScheduledDuties> scheduledDutiesFuture = new SafeFuture<>();
  private final DutyLoader dutyLoader = mock(DutyLoader.class);
  private final ScheduledDuties scheduledDuties = mock(ScheduledDuties.class);

  private EpochDuties duties;

  @BeforeEach
  void setUp() {
    when(dutyLoader.loadDutiesForEpoch(EPOCH)).thenReturn(scheduledDutiesFuture);
    duties = EpochDuties.calculateDuties(dutyLoader, EPOCH);
    verify(dutyLoader).loadDutiesForEpoch(EPOCH);
  }

  @Test
  void cancel_shouldCancelFuture() {
    duties.cancel();
    assertThat(scheduledDutiesFuture).isCancelled();
  }

  @Test
  void onBlockProductionDue_shouldActImmediatelyIfDutiesLoaded() {
    scheduledDutiesFuture.complete(scheduledDuties);
    duties.onBlockProductionDue(ONE);

    verify(scheduledDuties).produceBlock(ONE);
  }

  @Test
  void onBlockProductionDue_shouldDeferUntilDutiesLoaded() {
    duties.onBlockProductionDue(ONE);
    verifyNoInteractions(scheduledDuties);

    scheduledDutiesFuture.complete(scheduledDuties);
    verify(scheduledDuties).produceBlock(ONE);
  }

  @Test
  void onAttestationProductionDue_shouldActImmediatelyIfDutiesLoaded() {
    scheduledDutiesFuture.complete(scheduledDuties);
    duties.onAttestationCreationDue(ONE);

    verify(scheduledDuties).produceAttestations(ONE);
  }

  @Test
  void onAttestationProductionDue_shouldDeferUntilDutiesLoaded() {
    duties.onAttestationCreationDue(ONE);
    verifyNoInteractions(scheduledDuties);

    scheduledDutiesFuture.complete(scheduledDuties);
    verify(scheduledDuties).produceAttestations(ONE);
  }

  @Test
  void onAttestationAggregationDue_shouldActImmediatelyIfDutiesLoaded() {
    scheduledDutiesFuture.complete(scheduledDuties);
    duties.onAttestationAggregationDue(ONE);

    verify(scheduledDuties).performAggregation(ONE);
  }

  @Test
  void onAttestationAggregationDue_shouldDeferUntilDutiesLoaded() {
    duties.onAttestationAggregationDue(ONE);
    verifyNoInteractions(scheduledDuties);

    scheduledDutiesFuture.complete(scheduledDuties);
    verify(scheduledDuties).performAggregation(ONE);
  }

  @Test
  void shouldPerformDelayedDutiesInOrder() {
    duties.onBlockProductionDue(ZERO);
    duties.onAttestationCreationDue(ZERO);
    duties.onAttestationAggregationDue(ZERO);
    duties.onBlockProductionDue(ONE);
    duties.onAttestationCreationDue(ONE);
    duties.onAttestationAggregationDue(ONE);

    scheduledDutiesFuture.complete(scheduledDuties);
    final InOrder inOrder = inOrder(scheduledDuties);
    inOrder.verify(scheduledDuties).produceBlock(ZERO);
    inOrder.verify(scheduledDuties).produceAttestations(ZERO);
    inOrder.verify(scheduledDuties).performAggregation(ZERO);
    inOrder.verify(scheduledDuties).produceBlock(ONE);
    inOrder.verify(scheduledDuties).produceAttestations(ONE);
    inOrder.verify(scheduledDuties).performAggregation(ONE);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldRecalculateDuties() {
    scheduledDutiesFuture.complete(scheduledDuties);

    final ScheduledDuties newDuties = mock(ScheduledDuties.class);
    final SafeFuture<ScheduledDuties> recalculatedDuties = new SafeFuture<>();
    when(dutyLoader.loadDutiesForEpoch(EPOCH)).thenReturn(recalculatedDuties);

    duties.recalculate();
    verify(dutyLoader, times(2)).loadDutiesForEpoch(EPOCH);

    // Should use new duties to perform actions
    duties.onBlockProductionDue(ZERO);
    verifyNoInteractions(scheduledDuties);

    recalculatedDuties.complete(newDuties);
    verify(newDuties).produceBlock(ZERO);
  }

  @Test
  void shouldNotUsePreviouslyRequestedDutiesReceivedAfterRecalculationStarted() {
    final ScheduledDuties newDuties = mock(ScheduledDuties.class);
    final SafeFuture<ScheduledDuties> recalculatedDuties = new SafeFuture<>();
    when(dutyLoader.loadDutiesForEpoch(EPOCH)).thenReturn(recalculatedDuties);

    duties.recalculate();
    assertThat(scheduledDutiesFuture).isCancelled();
    verify(dutyLoader, times(2)).loadDutiesForEpoch(EPOCH);

    duties.onBlockProductionDue(ZERO);

    // Old request completes and should be ignored.
    scheduledDutiesFuture.complete(scheduledDuties);
    verifyNoInteractions(scheduledDuties);

    // Duties are performed when recalculation completes
    recalculatedDuties.complete(newDuties);
    verify(newDuties).produceBlock(ZERO);
  }
}
