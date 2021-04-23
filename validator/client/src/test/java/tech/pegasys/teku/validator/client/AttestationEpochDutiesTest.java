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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.duties.AttestationScheduledDuties;

class AttestationEpochDutiesTest
    extends EpochDutiesTestBase<AttestationEpochDuties, AttestationScheduledDuties> {

  public AttestationEpochDutiesTest() {
    super(mock(AttestationScheduledDuties.class));
  }

  @Override
  protected AttestationEpochDuties calculateDuties(
      final DutyLoader<AttestationScheduledDuties> dutyLoader, final UInt64 epoch) {
    return AttestationEpochDuties.calculateDuties(dutyLoader, epoch);
  }

  @Test
  void onAttestationProductionDue_shouldActImmediatelyIfDutiesLoaded() {
    scheduledDutiesFuture.complete(scheduledDutiesOptional);
    duties.onAttestationCreationDue(ONE);

    verify(scheduledDuties).produceAttestations(ONE);
  }

  @Test
  void onAttestationProductionDue_shouldDeferUntilDutiesLoaded() {
    duties.onAttestationCreationDue(ONE);
    verifyNoInteractions(scheduledDuties);

    scheduledDutiesFuture.complete(scheduledDutiesOptional);
    verify(scheduledDuties).produceAttestations(ONE);
  }

  @Test
  void onAttestationAggregationDue_shouldActImmediatelyIfDutiesLoaded() {
    scheduledDutiesFuture.complete(scheduledDutiesOptional);
    duties.onAttestationAggregationDue(ONE);

    verify(scheduledDuties).performAggregation(ONE);
  }

  @Test
  void onAttestationAggregationDue_shouldDeferUntilDutiesLoaded() {
    duties.onAttestationAggregationDue(ONE);
    verifyNoInteractions(scheduledDuties);

    scheduledDutiesFuture.complete(scheduledDutiesOptional);
    verify(scheduledDuties).performAggregation(ONE);
  }

  @Test
  void shouldPerformDelayedDutiesInOrder() {
    duties.onAttestationCreationDue(ZERO);
    duties.onAttestationAggregationDue(ZERO);
    duties.onAttestationCreationDue(ONE);
    duties.onAttestationAggregationDue(ONE);

    scheduledDutiesFuture.complete(scheduledDutiesOptional);
    final InOrder inOrder = inOrder(scheduledDuties);
    inOrder.verify(scheduledDuties).produceAttestations(ZERO);
    inOrder.verify(scheduledDuties).performAggregation(ZERO);
    inOrder.verify(scheduledDuties).produceAttestations(ONE);
    inOrder.verify(scheduledDuties).performAggregation(ONE);
    inOrder.verifyNoMoreInteractions();
  }
}
