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

package tech.pegasys.teku.validator.client.duties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.Validator;

class ScheduledDutiesTest {

  public static final UInt64 TWO = UInt64.valueOf(2);
  private final Validator validator = mock(Validator.class);
  private final ValidatorDutyFactory dutyFactory = mock(ValidatorDutyFactory.class);

  private final ScheduledDuties duties =
      new ScheduledDuties(dutyFactory, Bytes32.fromHexString("0x838382"));

  @Test
  public void shouldDiscardMissedBlockProductionDuties() {
    final BlockProductionDuty duty0 = mockDuty(BlockProductionDuty.class);
    final BlockProductionDuty duty1 = mockDuty(BlockProductionDuty.class);
    final BlockProductionDuty duty2 = mockDuty(BlockProductionDuty.class);
    when(dutyFactory.createBlockProductionDuty(ZERO, validator)).thenReturn(duty0);
    when(dutyFactory.createBlockProductionDuty(ONE, validator)).thenReturn(duty1);
    when(dutyFactory.createBlockProductionDuty(TWO, validator)).thenReturn(duty2);
    duties.scheduleBlockProduction(ZERO, validator);
    duties.scheduleBlockProduction(ONE, validator);
    duties.scheduleBlockProduction(TWO, validator);

    duties.produceBlock(ONE);
    verify(duty1).performDuty();

    // Duty from slot zero was dropped
    duties.produceBlock(ZERO);
    verify(duty0, never()).performDuty();

    // But the duty for slot 2 is still performed as scheduled
    duties.produceBlock(TWO);
    verify(duty2).performDuty();
  }

  @Test
  public void shouldDiscardMissedAttestationProductionDuties() {
    final AttestationProductionDuty duty0 = mockDuty(AttestationProductionDuty.class);
    final AttestationProductionDuty duty1 = mockDuty(AttestationProductionDuty.class);
    final AttestationProductionDuty duty2 = mockDuty(AttestationProductionDuty.class);
    when(dutyFactory.createAttestationProductionDuty(ZERO)).thenReturn(duty0);
    when(dutyFactory.createAttestationProductionDuty(ONE)).thenReturn(duty1);
    when(dutyFactory.createAttestationProductionDuty(TWO)).thenReturn(duty2);

    ignoreFuture(duties.scheduleAttestationProduction(ZERO, validator, 0, 0, 10, 5));
    ignoreFuture(duties.scheduleAttestationProduction(ONE, validator, 0, 0, 10, 5));
    ignoreFuture(duties.scheduleAttestationProduction(TWO, validator, 0, 0, 10, 5));

    duties.produceAttestations(ONE);
    verify(duty1).performDuty();

    // Duty from slot zero was dropped
    duties.produceAttestations(ZERO);
    verify(duty0, never()).performDuty();

    // But the duty for slot 2 is still performed as scheduled
    duties.produceAttestations(TWO);
    verify(duty2).performDuty();
  }

  @Test
  public void shouldDiscardMissedAggregationDuties() {
    final AggregationDuty duty0 = mockDuty(AggregationDuty.class);
    final AggregationDuty duty1 = mockDuty(AggregationDuty.class);
    final AggregationDuty duty2 = mockDuty(AggregationDuty.class);
    when(dutyFactory.createAggregationDuty(ZERO)).thenReturn(duty0);
    when(dutyFactory.createAggregationDuty(ONE)).thenReturn(duty1);
    when(dutyFactory.createAggregationDuty(TWO)).thenReturn(duty2);

    duties.scheduleAggregationDuties(
        ZERO, validator, 0, BLSSignature.empty(), 0, new SafeFuture<>());
    duties.scheduleAggregationDuties(
        ONE, validator, 0, BLSSignature.empty(), 0, new SafeFuture<>());
    duties.scheduleAggregationDuties(
        TWO, validator, 0, BLSSignature.empty(), 0, new SafeFuture<>());

    duties.performAggregation(ONE);
    verify(duty1).performDuty();

    // Duty from slot zero was dropped
    duties.performAggregation(ZERO);
    verify(duty0, never()).performDuty();

    // But the duty for slot 2 is still performed as scheduled
    duties.performAggregation(TWO);
    verify(duty2).performDuty();
  }

  private <T extends Duty> T mockDuty(final Class<T> dutyType) {
    final T mockDuty = mock(dutyType);
    when(mockDuty.performDuty()).thenReturn(new SafeFuture<>());
    return mockDuty;
  }
}
