/*
 * Copyright 2021 ConsenSys AG.
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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.duties.BlockProductionScheduledDuties;

public class BlockEpochDutiesTest
    extends EpochDutiesTestBase<BlockEpochDuties, BlockProductionScheduledDuties> {

  protected BlockEpochDutiesTest() {
    super(mock(BlockProductionScheduledDuties.class));
  }

  @Override
  protected BlockEpochDuties calculateDuties(
      final DutyLoader<BlockProductionScheduledDuties> dutyLoader, final UInt64 epoch) {
    return BlockEpochDuties.calculateDuties(dutyLoader, epoch);
  }

  @Test
  void onBlockProductionDue_shouldActImmediatelyIfDutiesLoaded() {
    scheduledDutiesFuture.complete(scheduledDutiesOptional);
    duties.onBlockProductionDue(ONE);

    verify(scheduledDuties).produceBlock(ONE);
  }

  @Test
  void onBlockProductionDue_shouldDeferUntilDutiesLoaded() {
    duties.onBlockProductionDue(ONE);
    verifyNoInteractions(scheduledDuties);

    scheduledDutiesFuture.complete(scheduledDutiesOptional);
    verify(scheduledDuties).produceBlock(ONE);
  }

  @Test
  void shouldPerformDelayedDutiesInOrder() {
    duties.onBlockProductionDue(ZERO);
    duties.onBlockProductionDue(ONE);

    scheduledDutiesFuture.complete(scheduledDutiesOptional);
    final InOrder inOrder = inOrder(scheduledDuties);
    inOrder.verify(scheduledDuties).produceBlock(ZERO);
    inOrder.verify(scheduledDuties).produceBlock(ONE);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void shouldRecalculateDuties() {
    scheduledDutiesFuture.complete(scheduledDutiesOptional);

    final BlockProductionScheduledDuties newDuties = mock(BlockProductionScheduledDuties.class);
    final SafeFuture<Optional<BlockProductionScheduledDuties>> recalculatedDuties =
        new SafeFuture<>();
    when(dutyLoader.loadDutiesForEpoch(EPOCH)).thenReturn(recalculatedDuties);

    duties.recalculate();
    verify(dutyLoader, times(2)).loadDutiesForEpoch(EPOCH);

    // Should use new duties to perform actions
    duties.onBlockProductionDue(ZERO);
    verifyNoInteractions(scheduledDuties);

    recalculatedDuties.complete(Optional.of(newDuties));
    verify(newDuties).produceBlock(ZERO);
  }

  @Test
  void shouldNotUsePreviouslyRequestedDutiesReceivedAfterRecalculationStarted() {
    final BlockProductionScheduledDuties newDuties = mock(BlockProductionScheduledDuties.class);
    final SafeFuture<Optional<BlockProductionScheduledDuties>> recalculatedDuties =
        new SafeFuture<>();
    when(dutyLoader.loadDutiesForEpoch(EPOCH)).thenReturn(recalculatedDuties);

    duties.recalculate();
    assertThat(scheduledDutiesFuture).isCancelled();
    verify(dutyLoader, times(2)).loadDutiesForEpoch(EPOCH);

    duties.onBlockProductionDue(ZERO);

    // Old request completes and should be ignored.
    scheduledDutiesFuture.complete(scheduledDutiesOptional);
    verifyNoInteractions(scheduledDuties);

    // Duties are performed when recalculation completes
    recalculatedDuties.complete(Optional.of(newDuties));
    verify(newDuties).produceBlock(ZERO);
  }

  @Test
  void shouldNotPerformActionsIfLoadedDutiesAreEmpty() {
    when(dutyLoader.loadDutiesForEpoch(EPOCH))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    duties.recalculate();

    duties.onBlockProductionDue(ONE);

    // Should have discarded this one even though no replacement was available.
    verifyNoInteractions(scheduledDuties);
  }
}
