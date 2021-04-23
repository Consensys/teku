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

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.Validator;

public class ScheduledDuties<P extends Duty, A extends Duty> {

  protected final NavigableMap<UInt64, P> productionDuties = new TreeMap<>();
  protected final NavigableMap<UInt64, A> aggregationDuties = new TreeMap<>();

  private final DutyFactory<P> productionDutyFactory;
  private final DutyFactory<A> aggregationDutyFactory;
  private final Bytes32 dependentRoot;
  private final LabelledMetric<Counter> dutiesPerformedCounter;

  public ScheduledDuties(
      final DutyFactory<P> productionDutyFactory,
      final DutyFactory<A> aggregationDutyFactory,
      final Bytes32 dependentRoot,
      final MetricsSystem metricsSystem) {
    this.productionDutyFactory = productionDutyFactory;
    this.aggregationDutyFactory = aggregationDutyFactory;
    this.dependentRoot = dependentRoot;
    dutiesPerformedCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR,
            "duties_performed",
            "Count of the failed duties, by duty type",
            "type",
            "result");
  }

  public Bytes32 getDependentRoot() {
    return dependentRoot;
  }

  public synchronized void scheduleProduction(final UInt64 slot, final Validator validator) {
    scheduleProduction(slot, validator, duty -> null);
  }

  public synchronized <T> T scheduleProduction(
      final UInt64 slot, final Validator validator, final Function<P, T> addToDuty) {
    final P existingDuty =
        productionDuties.computeIfAbsent(
            slot, __ -> productionDutyFactory.createDuty(slot, validator));
    return addToDuty.apply(existingDuty);
  }

  public synchronized void scheduleAggregation(
      final UInt64 slot, final Validator validator, final Consumer<A> addToDuty) {
    final A existingDuty =
        aggregationDuties.computeIfAbsent(
            slot, __ -> aggregationDutyFactory.createDuty(slot, validator));
    addToDuty.accept(existingDuty);
  }

  public synchronized void performProductionDuty(final UInt64 slot) {
    performDutyForSlot(productionDuties, slot);
  }

  public synchronized void performAggregationDuty(final UInt64 slot) {
    performDutyForSlot(aggregationDuties, slot);
  }

  private void performDutyForSlot(
      final NavigableMap<UInt64, ? extends Duty> duties, final UInt64 slot) {
    discardDutiesBeforeSlot(duties, slot);

    final Duty duty = duties.remove(slot);
    if (duty == null) {
      return;
    }
    duty.performDuty()
        .finish(
            result -> reportDutySuccess(result, duty, slot),
            error -> reportDutyFailure(error, duty, slot));
  }

  private void reportDutyFailure(final Throwable error, final Duty duty, final UInt64 slot) {
    dutiesPerformedCounter.labels(duty.getProducedType(), "failed").inc();
    VALIDATOR_LOGGER.dutyFailed(duty.getProducedType(), slot, duty.getValidatorIdString(), error);
  }

  private void reportDutySuccess(final DutyResult result, final Duty duty, final UInt64 slot) {
    dutiesPerformedCounter.labels(duty.getProducedType(), "success").inc();
    result.report(duty.getProducedType(), slot, duty.getValidatorIdString(), VALIDATOR_LOGGER);
  }

  private void discardDutiesBeforeSlot(
      final NavigableMap<UInt64, ? extends Duty> duties, final UInt64 slot) {
    duties.subMap(UInt64.ZERO, true, slot, false).clear();
  }

  public synchronized int countDuties() {
    return productionDuties.size() + aggregationDuties.size();
  }

  public interface DutyFactory<D extends Duty> {
    D createDuty(UInt64 slot, Validator validator);
  }
}
