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

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.Validator;

public class SlotBasedScheduledDuties<P extends Duty, A extends Duty> implements ScheduledDuties {

  protected final NavigableMap<UInt64, P> productionDuties = new TreeMap<>();
  protected final NavigableMap<UInt64, A> aggregationDuties = new TreeMap<>();

  private final DutyFactory<P, A> dutyFactory;
  private final Bytes32 dependentRoot;

  public SlotBasedScheduledDuties(
      final DutyFactory<P, A> dutyFactory, final Bytes32 dependentRoot) {
    this.dutyFactory = dutyFactory;
    this.dependentRoot = dependentRoot;
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
    return duty.performDuty();
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
