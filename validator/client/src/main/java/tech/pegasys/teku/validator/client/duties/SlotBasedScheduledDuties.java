/*
 * Copyright Consensys Software Inc., 2022
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

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.Validator;

public class SlotBasedScheduledDuties<P extends Duty, A extends Duty> implements ScheduledDuties {

  protected final NavigableMap<UInt64, P> productionDuties = new TreeMap<>();
  protected final NavigableMap<UInt64, A> aggregationDuties = new TreeMap<>();

  private final DutyFactory<P, A> dutyFactory;
  private final Bytes32 dependentRoot;

  private final OperationTimer attestationProductionDutyTimer;
  private final OperationTimer blockProductionDutyTimer;
  private final OperationTimer attestationAggregationDutyTimer;

  public SlotBasedScheduledDuties(
      final DutyFactory<P, A> dutyFactory,
      final Bytes32 dependentRoot,
      final MetricsSystem metricsSystem) {
    this.dutyFactory = dutyFactory;
    this.dependentRoot = dependentRoot;

    this.attestationProductionDutyTimer =
        metricsSystem.createTimer(
            TekuMetricCategory.VALIDATOR,
            "attestation_duty_timer",
            "Timer recording the time taken to perform an attestation duty");
    this.blockProductionDutyTimer =
        metricsSystem.createTimer(
            TekuMetricCategory.VALIDATOR,
            "block_duty_timer",
            "Timer recording the time taken to perform a block duty");
    this.attestationAggregationDutyTimer =
        metricsSystem.createTimer(
            TekuMetricCategory.VALIDATOR,
            "aggregation_duty_timer",
            "Timer recording the time taken to perform an aggregation duty");
  }

  public Bytes32 getDependentRoot() {
    return dependentRoot;
  }

  public synchronized void scheduleProduction(final UInt64 slot, final Validator validator) {
    scheduleProduction(slot, validator, duty -> null);
  }

  public synchronized <T> T scheduleProduction(
      final UInt64 slot, final Validator validator, final Function<P, T> addToDuty) {
    final P duty =
        productionDuties.computeIfAbsent(
            slot, __ -> dutyFactory.createProductionDuty(slot, validator));
    return addToDuty.apply(duty);
  }

  public synchronized void scheduleAggregation(
      final UInt64 slot, final Validator validator, final Consumer<A> addToDuty) {
    final A duty =
        aggregationDuties.computeIfAbsent(
            slot, __ -> dutyFactory.createAggregationDuty(slot, validator));
    addToDuty.accept(duty);
  }

  @Override
  public synchronized SafeFuture<DutyResult> performProductionDuty(final UInt64 slot) {
    return performDutyForSlot(productionDuties, slot);
  }

  @Override
  public String getProductionType() {
    return dutyFactory.getProductionType();
  }

  @Override
  public synchronized SafeFuture<DutyResult> performAggregationDuty(final UInt64 slot) {
    return performDutyForSlot(aggregationDuties, slot);
  }

  @Override
  public String getAggregationType() {
    return dutyFactory.getAggregationType();
  }

  private SafeFuture<DutyResult> performDutyForSlot(
      final NavigableMap<UInt64, ? extends Duty> duties, final UInt64 slot) {
    discardDutiesBeforeSlot(duties, slot);

    final Duty duty = duties.remove(slot);
    if (duty == null) {
      return SafeFuture.completedFuture(DutyResult.NO_OP);
    }

    final boolean metricsOn = true; // todo work out if metrics are turned on
    return metricsOn ? performDutyWithMetrics(duty) : duty.performDuty();
  }

  private OperationTimer getMetricTimer(final DutyType type) {
    if (type.equals(DutyType.ATTESTATION_AGGREGATION)) {
      return attestationAggregationDutyTimer;
    } else if (type.equals(DutyType.ATTESTATION_PRODUCTION)) {
      return attestationProductionDutyTimer;
    } else if (type.equals(DutyType.BLOCK_PRODUCTION)) {
      return blockProductionDutyTimer;
    } else {
      throw new InvalidConfigurationException(type.getType() + " is an invalid duty type");
    }
  }

  private SafeFuture<DutyResult> performDutyWithMetrics(final Duty duty) {
    final OperationTimer timer = getMetricTimer(duty.getType());
    final OperationTimer.TimingContext context = timer.startTimer();
    return duty.performDuty()
        .thenApply(
            result -> {
              context.stopTimer();
              return result;
            });
  }

  private void discardDutiesBeforeSlot(
      final NavigableMap<UInt64, ? extends Duty> duties, final UInt64 slot) {
    duties.subMap(UInt64.ZERO, true, slot, false).clear();
  }

  @Override
  public synchronized int countDuties() {
    return productionDuties.size() + aggregationDuties.size();
  }

  @Override
  public boolean requiresRecalculation(final Bytes32 newHeadDependentRoot) {
    return !getDependentRoot().equals(newHeadDependentRoot);
  }
}
