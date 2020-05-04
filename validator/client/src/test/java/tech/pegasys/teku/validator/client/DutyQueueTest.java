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

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

class DutyQueueTest {
  private final SafeFuture<ScheduledDuties> scheduledDutiesFuture = new SafeFuture<>();
  private final ScheduledDuties scheduledDuties = mock(ScheduledDuties.class);

  private final DutyQueue duties = new DutyQueue(scheduledDutiesFuture);

  @Test
  public void cancel_shouldCancelFuture() {
    duties.cancel();
    assertThat(scheduledDutiesFuture).isCancelled();
  }

  @Test
  public void onBlockProductionDue_shouldActImmediatelyIfDutiesLoaded() {
    scheduledDutiesFuture.complete(scheduledDuties);
    duties.onBlockProductionDue(ONE);

    verify(scheduledDuties).produceBlock(ONE);
  }

  @Test
  public void onBlockProductionDue_shouldDeferUntilDutiesLoaded() {
    duties.onBlockProductionDue(ONE);
    verifyNoInteractions(scheduledDuties);

    scheduledDutiesFuture.complete(scheduledDuties);
    verify(scheduledDuties).produceBlock(ONE);
  }

  @Test
  public void onAttestationProductionDue_shouldActImmediatelyIfDutiesLoaded() {
    scheduledDutiesFuture.complete(scheduledDuties);
    duties.onAttestationCreationDue(ONE);

    verify(scheduledDuties).produceAttestations(ONE);
  }

  @Test
  public void onAttestationProductionDue_shouldDeferUntilDutiesLoaded() {
    duties.onAttestationCreationDue(ONE);
    verifyNoInteractions(scheduledDuties);

    scheduledDutiesFuture.complete(scheduledDuties);
    verify(scheduledDuties).produceAttestations(ONE);
  }

  @Test
  public void onAttestationAggregationDue_shouldActImmediatelyIfDutiesLoaded() {
    scheduledDutiesFuture.complete(scheduledDuties);
    duties.onAttestationAggregationDue(ONE);

    verify(scheduledDuties).performAggregation(ONE);
  }

  @Test
  public void onAttestationAggregationDue_shouldDeferUntilDutiesLoaded() {
    duties.onAttestationAggregationDue(ONE);
    verifyNoInteractions(scheduledDuties);

    scheduledDutiesFuture.complete(scheduledDuties);
    verify(scheduledDuties).performAggregation(ONE);
  }

  @Test
  public void shouldPerformDelayedDutiesInOrder() {
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
}
