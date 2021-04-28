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

package tech.pegasys.teku.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubCounter;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.Validator;

class SlotBasedScheduledDutiesTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  public static final UInt64 TWO = UInt64.valueOf(2);
  private final Validator validator = mock(Validator.class);

  @SuppressWarnings("unchecked")
  private final Function<ProductionDuty, String> productionDutyAdder = mock(Function.class);

  @SuppressWarnings("unchecked")
  private final Consumer<AggregationDuty> aggregateDutyAdder = mock(Consumer.class);

  @SuppressWarnings("unchecked")
  private final DutyFactory<ProductionDuty, AggregationDuty> dutyFactory = mock(DutyFactory.class);

  final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final SlotBasedScheduledDuties<ProductionDuty, AggregationDuty> duties =
      new SlotBasedScheduledDuties<>(dutyFactory, Bytes32.fromHexString("0x838382"), metricsSystem);

  @Test
  public void shouldDiscardMissedProductionDuties() {
    final ProductionDuty duty0 = mockDuty(ProductionDuty.class);
    final ProductionDuty duty1 = mockDuty(ProductionDuty.class);
    final ProductionDuty duty2 = mockDuty(ProductionDuty.class);
    when(dutyFactory.createProductionDuty(ZERO, validator)).thenReturn(duty0);
    when(dutyFactory.createProductionDuty(ONE, validator)).thenReturn(duty1);
    when(dutyFactory.createProductionDuty(TWO, validator)).thenReturn(duty2);

    duties.scheduleProduction(ZERO, validator, productionDutyAdder);
    duties.scheduleProduction(ONE, validator, productionDutyAdder);
    duties.scheduleProduction(TWO, validator, productionDutyAdder);

    duties.performProductionDuty(ONE);
    verify(duty1).performDuty();

    // Duty from slot zero was dropped
    duties.performProductionDuty(ZERO);
    verify(duty0, never()).performDuty();

    // But the duty for slot 2 is still performed as scheduled
    duties.performProductionDuty(TWO);
    verify(duty2).performDuty();
    validateMetrics(duty0.getProducedType(), 2, 0);
  }

  @Test
  public void shouldDiscardMissedAggregationDuties() {
    final AggregationDuty duty0 = mockDuty(AggregationDuty.class);
    final AggregationDuty duty1 = mockDuty(AggregationDuty.class);
    final AggregationDuty duty2 = mockDuty(AggregationDuty.class);
    when(dutyFactory.createAggregationDuty(ZERO, validator)).thenReturn(duty0);
    when(dutyFactory.createAggregationDuty(ONE, validator)).thenReturn(duty1);
    when(dutyFactory.createAggregationDuty(TWO, validator)).thenReturn(duty2);

    duties.scheduleAggregation(ZERO, validator, aggregateDutyAdder);
    duties.scheduleAggregation(ONE, validator, aggregateDutyAdder);
    duties.scheduleAggregation(TWO, validator, aggregateDutyAdder);

    duties.performAggregationDuty(ONE);
    verify(duty1).performDuty();

    // Duty from slot zero was dropped
    duties.performAggregationDuty(ZERO);
    verify(duty0, never()).performDuty();

    // But the duty for slot 2 is still performed as scheduled
    duties.performAggregationDuty(TWO);
    verify(duty2).performDuty();

    validateMetrics(duty0.getProducedType(), 2, 0);
  }

  private <T extends Duty> T mockDuty(final Class<T> dutyType) {
    final T mockDuty = mock(dutyType);
    when(mockDuty.getProducedType()).thenReturn(dutyType.getSimpleName());
    when(mockDuty.performDuty())
        .thenReturn(
            SafeFuture.completedFuture(DutyResult.success(dataStructureUtil.randomBytes32())));
    return mockDuty;
  }

  private void validateMetrics(final String duty, final long successCount, final long failCount) {
    final StubCounter labelledCounter =
        metricsSystem.getCounter(TekuMetricCategory.VALIDATOR, "duties_performed");
    assertThat(labelledCounter.getValue(duty, "success")).isEqualTo(successCount);
    assertThat(labelledCounter.getValue(duty, "failed")).isEqualTo(failCount);
  }

  private interface ProductionDuty extends Duty {}

  private interface AggregationDuty extends Duty {}
}
